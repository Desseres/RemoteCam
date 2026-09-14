// Run the actual browser receiver script with deterministic WebRTC/DOM test doubles.
// No camera, network, npm dependencies or media recordings are needed.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const html = fs.readFileSync(path.join(__dirname, '../app/src/main/resources/webrtc.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];

function receiver(types, search = '') {
    const nodes = Object.fromEntries(['video', 'status', 'message', 'stats', 'sound'].map(id => [id, {
        hidden: false, textContent: '', dataset: {}, play: async () => {},
    }]));
    const sockets = [], peers = [], timeouts = [], events = {};
    class Socket {
        static OPEN = 1;
        readyState = 1;
        sent = [];
        constructor() { sockets.push(this); }
        send(data) { this.sent.push(data); }
        close() { if (this.readyState === 3) return; this.readyState = 3; this.onclose?.(); }
    }
    class Peer {
        transceivers = [];
        constructor() { peers.push(this); }
        addTransceiver(kind, options) {
            const t = { kind, direction: options.direction, setCodecPreferences(codecs) { this.codecs = Array.from(codecs); } };
            this.transceivers.push(t); return t;
        }
        async createOffer() { return { type: 'offer', sdp: 'test-offer' }; }
        async setLocalDescription(offer) { this.localDescription = offer; }
        async setRemoteDescription(answer) { this.remoteDescription = answer; }
        async addIceCandidate() {}
        close() { this.closed = true; }
    }
    class Stream {
        tracks = [];
        addTrack(track) { this.tracks.push(track); }
        getAudioTracks() { return this.tracks.filter(t => t.kind === 'audio'); }
    }
    vm.runInNewContext(script, {
        document: { getElementById: id => nodes[id] }, location: { protocol: 'http:', host: 'phone:8080', search },
        URLSearchParams, performance: { now: () => 0 }, WebSocket: Socket, RTCPeerConnection: Peer, MediaStream: Stream,
        RTCRtpReceiver: { getCapabilities: kind => ({ codecs: (kind === 'video' ? types : ['audio/opus']).map(mimeType => ({ mimeType })) }) },
        setInterval: () => 1, clearInterval() {}, setTimeout: fn => { timeouts.push(fn); return 1; }, clearTimeout() {},
        window: { addEventListener: (name, fn) => { events[name] = fn; } },
    });
    return { nodes, sockets, peers, timeouts, events };
}

test('dual-codec receiver offers H264 and H265 plus retransmission codecs, receive-only', async () => {
    const r = receiver(['video/VP8', 'video/H265', 'video/rtx', 'video/H264', 'video/AV1']);
    assert.deepEqual(r.peers[0].transceivers[0].codecs.map(c => c.mimeType), ['video/H264', 'video/H265', 'video/rtx']);
    for (const t of r.peers[0].transceivers) assert.equal(t.direction, 'recvonly');
    await r.sockets[0].onopen();
    assert.deepEqual(r.sockets[0].sent, ['offer\ntest-offer']);
});

test('H264-only browser does not claim HEVC support', async () => {
    const r = receiver(['video/H264', 'video/rtx']);
    assert.deepEqual(r.peers[0].transceivers[0].codecs.map(c => c.mimeType), ['video/H264', 'video/rtx']);
    await r.sockets[0].onopen();
    assert.equal(r.sockets[0].sent.length, 1);
});

test('HEVC-only receiver can offer H265 without H264', async () => {
    const r = receiver(['video/H265']);
    assert.deepEqual(r.peers[0].transceivers[0].codecs.map(c => c.mimeType), ['video/H265']);
    await r.sockets[0].onopen();
    assert.equal(r.sockets[0].sent.length, 1);
});

test('unsupported browser shows an actionable error without sending a different codec', async () => {
    const r = receiver(['video/VP8', 'video/AV1']);
    await r.sockets[0].onopen();
    assert.equal(r.sockets[0].sent.length, 0);
    assert.match(r.nodes.message.textContent, /does not support H.264 or H.265/);
    assert.equal(r.peers[0].closed, true);
});

test('codec mismatch from phone is visible and reconnect preserves the receiver capabilities', async () => {
    const r = receiver(['video/H264']);
    await r.sockets[0].onopen();
    r.sockets[0].onmessage({ data: 'error\nReceiver does not offer the selected H.265 codec. Select H.264 + WebRTC on the phone.' });
    await new Promise(resolve => setImmediate(resolve));
    assert.match(r.nodes.message.textContent, /selected H.265 codec/);
    assert.equal(r.nodes.status.hidden, false);
    r.timeouts[0]();
    assert.equal(r.peers.length, 2);
    assert.deepEqual(r.peers[1].transceivers[0].codecs.map(c => c.mimeType), ['video/H264']);
    r.events.pagehide();
    assert.equal(r.peers[1].closed, true);
});

test('HEVC playback retains optional audio and muted receiver behavior', async () => {
    const r = receiver(['video/H265'], '?muted=1');
    await r.peers[0].ontrack({ track: { kind: 'video' } });
    await r.peers[0].ontrack({ track: { kind: 'audio' } });
    assert.equal(r.nodes.video.muted, true);
    assert.equal(r.nodes.sound.hidden, false);
    await r.nodes.sound.onclick();
    assert.equal(r.nodes.video.muted, false);
    assert.equal(r.nodes.sound.hidden, true);
    assert.equal(r.nodes.video.srcObject.tracks.length, 2);
});
