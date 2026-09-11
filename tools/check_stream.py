"""Validate live MJPEG framing and JPEG dimensions without saving camera images.

Usage: python tools/check_stream.py http://PHONE:8080/cam.mjpeg --frames 60 --clients 2
"""
import argparse
import concurrent.futures
import json
import time
import urllib.request


def jpeg_size(data):
    assert data[:2] == b'\xff\xd8' and data[-2:] == b'\xff\xd9', 'Invalid JPEG markers'
    offset = 2
    while offset < len(data):
        assert data[offset] == 0xff, 'Invalid JPEG segment'
        while data[offset] == 0xff:
            offset += 1
        marker = data[offset]
        offset += 1
        length = int.from_bytes(data[offset:offset + 2], 'big')
        if marker in (0xc0, 0xc1, 0xc2):
            return (int.from_bytes(data[offset + 5:offset + 7], 'big'),
                    int.from_bytes(data[offset + 3:offset + 5], 'big'))
        assert length >= 2
        offset += length
    raise AssertionError('Missing JPEG dimensions')


def check(url, frames):
    start = time.monotonic()
    total = 0
    sizes = set()
    with urllib.request.urlopen(url, timeout=10) as response:
        assert response.status == 200
        assert 'multipart/x-mixed-replace' in response.headers['Content-Type']
        for _ in range(frames):
            assert response.readline() == b'--FRAME\r\n', 'Invalid multipart boundary'
            headers = {}
            while (line := response.readline()) != b'\r\n':
                assert line, 'Stream ended in headers'
                name, value = line.decode('ascii').split(':', 1)
                headers[name.lower()] = value.strip()
            assert headers['content-type'] == 'image/jpeg'
            length = int(headers['content-length'])
            assert 0 < length < 50_000_000
            data = response.read(length)
            assert len(data) == length
            sizes.add(jpeg_size(data))
            total += length
            assert response.read(2) == b'\r\n'
    elapsed = time.monotonic() - start
    return dict(frames=frames, seconds=round(elapsed, 2), fps=round(frames / elapsed, 1),
                megabytes=round(total / 1_000_000, 2), dimensions=sorted(sizes))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('url')
    parser.add_argument('--frames', type=int, default=60)
    parser.add_argument('--clients', type=int, default=2)
    args = parser.parse_args()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.clients) as executor:
        results = list(executor.map(lambda _: check(args.url, args.frames), range(args.clients)))
    print(json.dumps(results, indent=2))
