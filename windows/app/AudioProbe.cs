using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace RemoteCamDesktop
{
    static class AudioProbe
    {
        public static async Task<int> Run(string address, string resultPath)
        {
            var receiver = new Receiver(); var audio = new AudioModule();
            var log = new System.Text.StringBuilder();
            try {
                var outputs = CableDevice.List();
                if (outputs.Count != 1) throw new IOException("Expected one VB-CABLE render endpoint.");
                using (var devices = new MMDeviceEnumerator()) {
                    MMDevice input = null;
                    foreach (var device in devices.EnumerateAudioEndPoints(DataFlow.Capture, DeviceState.Active)) {
                        if (CableDevice.IsCable(device) && !device.FriendlyName.Contains("16ch") && input == null) input = device;
                        else device.Dispose();
                    }
                    if (input == null) throw new IOException("VB-CABLE capture endpoint missing.");
                    using (input) using (var capture = new WasapiCapture(input)) {
                        long nonzero = 0, bytes = 0;
                        capture.DataAvailable += delegate(object sender, WaveInEventArgs e) {
                            Interlocked.Add(ref bytes, e.BytesRecorded);
                            // WASAPI shared capture normally uses IEEE float; retain no audio.
                            if (capture.WaveFormat.BitsPerSample == 32)
                                for (int i = 0; i + 4 <= e.BytesRecorded; i += 4)
                                    if (Math.Abs(BitConverter.ToSingle(e.Buffer, i)) > .00001) Interlocked.Increment(ref nonzero);
                        };
                        capture.StartRecording();
                        await receiver.Start(address, false); audio.Start(receiver, outputs[0].Id);
                        await Task.Delay(15000);
                        long live = Interlocked.Read(ref nonzero);
                        log.AppendLine("receivedPcmBytes=" + Interlocked.Read(ref audio.ReceivedBytes));
                        log.AppendLine("capture=" + input.FriendlyName + " format=" + capture.WaveFormat);
                        log.AppendLine("liveNonzeroSamples=" + live + " capturedBytes=" + Interlocked.Read(ref bytes));
                        audio.Buffer.Muted = true; await Task.Delay(1000);
                        long mutedStart = Interlocked.Read(ref nonzero);
                        await Task.Delay(4000);
                        long muted = Interlocked.Read(ref nonzero) - mutedStart;
                        log.AppendLine("mutedNonzeroSamples=" + muted);
                        audio.Buffer.Muted = false;
                        long resumeStart = Interlocked.Read(ref nonzero); await Task.Delay(4000);
                        long resumed = Interlocked.Read(ref nonzero) - resumeStart;
                        log.AppendLine("resumedNonzeroSamples=" + resumed);
                        await audio.Stop();
                        await Task.Delay(1000); long stoppedStart = Interlocked.Read(ref nonzero); await Task.Delay(2000);
                        long stopped = Interlocked.Read(ref nonzero) - stoppedStart;
                        log.AppendLine("stoppedNonzeroSamples=" + stopped);
                        log.AppendLine("status=" + audio.Status);
                        capture.StopRecording();
                        return live > 0 && muted == 0 && resumed > 0 && stopped == 0 ? 0 : 2;
                    }
                }
            } catch (Exception e) { log.AppendLine(e.ToString()); return 1; }
            finally { await audio.Stop(); await receiver.Stop(); receiver.Dispose(); File.WriteAllText(resultPath, log.ToString()); }
        }
    }
}
