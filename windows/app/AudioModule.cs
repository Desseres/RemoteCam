using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace RemoteCamDesktop
{
    sealed class CableDevice
    {
        public string Id, Name;
        public override string ToString() { return Name; }
        public static List<CableDevice> List()
        {
            var result = new List<CableDevice>();
            using (var devices = new MMDeviceEnumerator())
                foreach (var device in devices.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active))
                    using (device) {
                        // Never fall back to speakers or a Windows default endpoint.
                        if (IsCable(device) && !device.FriendlyName.Contains("16ch"))
                            result.Add(new CableDevice { Id = device.ID, Name = device.FriendlyName });
                    }
            return result;
        }
        public static bool IsCable(MMDevice device) { return device.DeviceFriendlyName == "VB-Audio Virtual Cable"; }
    }

    // Bounded PCM queue; muting is applied when WASAPI reads, including queued data.
    sealed class MicrophoneBuffer : IWaveProvider
    {
        readonly object gate = new object();
        readonly byte[] ring = new byte[48000 * 2 * 4 / 10]; // 100 ms, stereo float
        int start, length;
        public volatile bool Muted;
        public volatile float Gain = 1;
        public volatile float Peak;
        public long DiscardedBytes;
        public WaveFormat WaveFormat { get { return WaveFormat.CreateIeeeFloatWaveFormat(48000, 2); } }
        public int BufferedBytes { get { lock (gate) return length; } }
        public void Clear() { lock (gate) { start = length = 0; Peak = 0; } }
        public void Add(byte[] data, int count)
        {
            if (count % 8 != 0) throw new ArgumentException("PCM must contain complete stereo frames.");
            lock (gate) {
                int offset = Math.Max(0, count - ring.Length); count -= offset;
                int drop = Math.Max(0, length + count - ring.Length);
                start = (start + drop) % ring.Length; length -= drop;
                Interlocked.Add(ref DiscardedBytes, offset + drop);
                for (int i = 0; i < count; i++) ring[(start + length + i) % ring.Length] = data[offset + i];
                length += count;
            }
        }
        public unsafe int Read(byte[] buffer, int offset, int count)
        {
            lock (gate) {
                int available = Math.Min(count, length);
                for (int i = 0; i < available; i++) buffer[offset + i] = ring[(start + i) % ring.Length];
                start = (start + available) % ring.Length; length -= available;
                Array.Clear(buffer, offset + available, count - available);
                if (Muted) { Array.Clear(buffer, offset, count); Peak = 0; return count; }
                float peak = 0;
                fixed (byte* bytes = buffer) for (int i = offset; i + 4 <= offset + available; i += 4) {
                    float* samplePointer = (float*)(bytes + i);
                    float sample = *samplePointer * Gain;
                    if (Single.IsNaN(sample) || Single.IsInfinity(sample)) sample = 0;
                    sample = Math.Max(-1, Math.Min(1, sample)); peak = Math.Max(peak, Math.Abs(sample));
                    *samplePointer = sample;
                }
                Peak = peak; return count;
            }
        }
    }

    sealed class AudioModule
    {
        public readonly MicrophoneBuffer Buffer = new MicrophoneBuffer();
        public volatile string Status = "Mikrofon wyłączony. Odsłuch lokalny wyłączony.";
        public long ReceivedBytes;
        CancellationTokenSource cancellation;
        Task worker;
        Task stopping;
        readonly object gate = new object();
        Process decoder;
        public void Start(Receiver receiver, string deviceId)
        {
            if (worker != null) throw new InvalidOperationException("Audio is already running.");
            stopping = null;
            cancellation = new CancellationTokenSource();
            worker = Task.Run(async delegate {
                try { await Run(receiver, deviceId, cancellation.Token); }
                catch (Exception e) { Status = "Nie można uruchomić audio: " + e.Message; }
            });
        }
        async Task Run(Receiver receiver, string deviceId, CancellationToken token)
        {
            using (var devices = new MMDeviceEnumerator())
            using (var job = new ProcessJob()) {
                while (!token.IsCancellationRequested) {
                    Process process = null;
                    Task watchdog = null;
                    CancellationTokenSource attempt = null;
                    try {
                        string endpoint = receiver.AudioEndpoint;
                        if (endpoint == null) { Status = "Oczekiwanie na połączenie z telefonem…"; await Task.Delay(500, token); continue; }
                        using (var device = devices.GetDevice(deviceId)) {
                            if (!CableDevice.IsCable(device) || device.State != DeviceState.Active) throw new IOException("Wybrany VB-CABLE jest niedostępny.");
                            using (var output = new WasapiOut(device, AudioClientShareMode.Shared, true, 50)) {
                                output.Init(Buffer); Buffer.Clear(); output.Play();
                                string path = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "ffmpeg.exe");
                                var info = new ProcessStartInfo(path, "-hide_banner -loglevel warning -nostdin -rtsp_transport tcp -allowed_media_types audio -timeout 5000000 -i " + endpoint + " -vn -sn -dn -ac 2 -ar 48000 -f f32le pipe:1") {
                                    UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true
                                };
                                process = Process.Start(info); job.Add(process);
                                lock (gate) decoder = process;
                                process.ErrorDataReceived += delegate { }; process.BeginErrorReadLine();
                                long lastRead = Stopwatch.GetTimestamp();
                                attempt = CancellationTokenSource.CreateLinkedTokenSource(token);
                                var current = process;
                                watchdog = Task.Run(async delegate {
                                    while (!attempt.IsCancellationRequested) {
                                        await Task.Delay(500, attempt.Token);
                                        if (receiver.AudioEndpoint != endpoint || output.PlaybackState != PlaybackState.Playing ||
                                            (Stopwatch.GetTimestamp() - Interlocked.Read(ref lastRead)) / (double)Stopwatch.Frequency > 8) { Kill(current); break; }
                                    }
                                });
                                Status = "Łączenie audio… Włącz mikrofon na telefonie.";
                                byte[] chunk = new byte[3840]; // 10 ms
                                while (!token.IsCancellationRequested) {
                                    int read = 0;
                                    while (read < chunk.Length) {
                                        int count = process.StandardOutput.BaseStream.Read(chunk, read, chunk.Length - read);
                                        if (count == 0) throw new IOException("Brak audio — włącz mikrofon na telefonie. Ponawiam…");
                                        read += count;
                                    }
                                    Interlocked.Exchange(ref lastRead, Stopwatch.GetTimestamp());
                                    Interlocked.Add(ref ReceivedBytes, read); Buffer.Add(chunk, read);
                                    Status = "Audio → VB-CABLE · 48 kHz · odsłuch lokalny wyłączony";
                                }
                                output.Stop();
                            }
                        }
                    } catch (OperationCanceledException) { }
                    catch (Exception e) { Status = "Audio: " + e.Message; }
                    finally {
                        if (attempt != null) {
                            attempt.Cancel();
                            if (watchdog != null) { try { await watchdog; } catch (OperationCanceledException) { } }
                            attempt.Dispose();
                        }
                        lock (gate) { decoder = null; Kill(process); }
                        if (process != null) { try { process.WaitForExit(2000); } catch (InvalidOperationException) { } process.Dispose(); }
                        Buffer.Clear();
                    }
                    if (!token.IsCancellationRequested) { try { await Task.Delay(2000, token); } catch (OperationCanceledException) { } }
                }
            }
        }
        static void Kill(Process process) { try { if (process != null && !process.HasExited) process.Kill(); } catch (InvalidOperationException) { } }
        public Task Stop()
        {
            lock (gate) {
                if (stopping != null) return stopping;
                if (cancellation == null) return Task.CompletedTask;
                cancellation.Cancel(); Kill(decoder);
                return stopping = FinishStop();
            }
        }
        async Task FinishStop()
        {
            try { await worker; } finally { cancellation.Dispose(); cancellation = null; worker = null; Buffer.Clear(); Status = "Mikrofon wyłączony. Odsłuch lokalny wyłączony."; }
        }
    }
}
