using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.IO.Pipes;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Web.Script.Serialization;

namespace RemoteCamDesktop
{
    static class Brand
    {
        public const string Version = BuildInfo.Version;
        public static Icon LoadIcon()
        {
            using (var stream = typeof(Brand).Assembly.GetManifestResourceStream("RemoteCam.Icon"))
            using (var icon = new Icon(stream, 32, 32)) return (Icon)icon.Clone();
        }
        public static Image LoadLogo()
        {
            using (var stream = typeof(Brand).Assembly.GetManifestResourceStream("RemoteCam.Logo"))
            using (var image = Image.FromStream(stream)) return new Bitmap(image);
        }
    }
    static class Program
    {
        [DllImport("shell32.dll", CharSet = CharSet.Unicode)] static extern int SetCurrentProcessExplicitAppUserModelID(string id);
        [STAThread]
        static int Main(string[] args)
        {
            SetCurrentProcessExplicitAppUserModelID("RemoteCam.Desktop");
            if (args.Length == 3 && args[0] == "--audio-test") return AudioProbe.Run(args[1], args[2]).GetAwaiter().GetResult();
            if (args.Length == 3 && args[0] == "--tray-test") {
                Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
                int result = 1;
                using (var window = new MainWindow()) {
                    window.ShowInTaskbar = false; window.StartPosition = FormStartPosition.Manual; window.Location = new Point(-32000, -32000);
                    window.Shown += async delegate {
                        result = await window.MeasureTray(args[1], args[2]);
                        window.FormClosed += delegate { File.AppendAllText(args[2], "explicitExit=true\n"); };
                        window.exitRequested = true; window.Close();
                    };
                    Application.Run(window);
                }
                return result;
            }
            if (args.Length > 0 && args[0] == "--smoke")
                return Smoke(args).GetAwaiter().GetResult();
            if (args.Length > 0 && args[0] == "--self-test") return SelfTest();
            if (args.Length >= 3 && args[0] == "--preview-test")
            {
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                int result = 1;
                using (var window = new MainWindow(true))
                {
                    window.ShowInTaskbar = false; window.StartPosition = FormStartPosition.Manual;
                    window.Location = new Point(-32000, -32000);
                    window.Shown += async delegate
                    {
                        result = await window.MeasurePreview(args[1], Int32.Parse(args[2]));
                        window.Close();
                    };
                    Application.Run(window);
                }
                return result;
            }
            if (args.Length == 2 && (args[0] == "--render-ui" || args[0] == "--render-hardware"))
            {
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                using (var window = new MainWindow(true))
                {
                    window.ShowInTaskbar = false;
                    window.StartPosition = FormStartPosition.Manual;
                    window.Location = new Point(-32000, -32000);
                    window.Show();
                    if (args[0] == "--render-hardware") window.ShowHardwareForRender();
                    Application.DoEvents();
                    using (var bitmap = new Bitmap(window.Width, window.Height))
                    {
                        window.DrawToBitmap(bitmap, new Rectangle(0, 0, bitmap.Width, bitmap.Height));
                        bitmap.Save(args[1], ImageFormat.Png);
                    }
                    window.Hide();
                }
                return 0;
            }
            bool first;
            using (var instance = new Mutex(true, "Local\\RemoteCam.Desktop", out first))
            {
                if (!first) { MessageBox.Show("RemoteCam jest już uruchomiony."); return 1; }
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new MainWindow());
            }
            return 0;
        }

        static async Task<int> Smoke(string[] args)
        {
            string logPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "smoke-result.txt");
            var log = new StringBuilder();
            using (var receiver = new Receiver())
            {
                receiver.Status = delegate(string s) { lock (log) log.AppendLine(s); };
                try
                {
                    await receiver.Start(args.Length > 1 ? args[1] : "", Array.IndexOf(args, "--no-camera") < 0);
                    int seconds = 15;
                    if (args.Length > 2) Int32.TryParse(args[2], out seconds);
                    await Task.Delay(Math.Max(3, seconds) * 1000);
                    long frames = receiver.Frames;
                    log.AppendLine("frames=" + frames + " codec=" + receiver.Codec + " pipeReads=" + receiver.ReadCalls);
                    await receiver.Stop();
                    File.WriteAllText(logPath, log.ToString());
                    return frames >= 30 ? 0 : 2;
                }
                catch (Exception e)
                {
                    log.AppendLine(e.ToString());
                    File.WriteAllText(logPath, log.ToString());
                    return 1;
                }
            }
        }

        static int SelfTest()
        {
            if (Receiver.PhoneUri("192.168.1.11").AbsoluteUri != "http://192.168.1.11:8080/whep") return 1;
            if (Receiver.PhoneUri("http://192.168.1.11:8080/webrtc?stats=1").AbsoluteUri != "http://192.168.1.11:8080/whep") return 2;
            string[] invalid = { "", "file:///C:/test", "http://user:pass@localhost", "http://localhost:8080/\"x" };
            foreach (string value in invalid)
            {
                try { Receiver.PhoneUri(value); return 3; } catch (ArgumentException) { }
            }
            var black = new byte[FrameBroker.FrameSize];
            for (int i = 0; i < black.Length; ++i) black[i] = (byte)(i < 1920 * 1080 ? 16 : 128);
            using (var bitmap = Preview.Create(black))
                if (bitmap.GetPixel(5, 5).ToArgb() != Color.Black.ToArgb()) return 4;
            return 0;
        }
    }

    sealed class FrameBroker : IDisposable
    {
        public const int Width = 1920, Height = 1080, FrameSize = Width * Height * 3 / 2;
        public readonly string Name = "RemoteCam." + Guid.NewGuid().ToString("N");
        readonly CancellationTokenSource stop = new CancellationTokenSource();
        readonly object gate = new object();
        readonly List<NamedPipeServerStream> pipes = new List<NamedPipeServerStream>();
        byte[] latest;
        TaskCompletionSource<bool> changed = NewSignal();
        static TaskCompletionSource<bool> NewSignal() { return new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously); }
        long published;
        readonly Stopwatch clock = Stopwatch.StartNew();
        public void Publish(byte[] frame)
        {
            TaskCompletionSource<bool> signal;
            lock (gate) { latest = frame; published = clock.ElapsedMilliseconds; signal = changed; changed = NewSignal(); }
            signal.TrySetResult(true);
        }
        public byte[] Latest { get { lock (gate) return latest != null && clock.ElapsedMilliseconds - published < 1500 ? latest : null; } }
        public void Clear() { lock (gate) latest = null; }

        public void Start() { Task.Run((Func<Task>)Accept); }
        async Task Accept()
        {
            while (!stop.IsCancellationRequested)
            {
                NamedPipeServerStream pipe = null;
                try
                {
                    var acl = new PipeSecurity();
                    acl.SetAccessRuleProtection(true, false);
                    acl.AddAccessRule(new PipeAccessRule(WindowsIdentity.GetCurrent().User, PipeAccessRights.FullControl, AccessControlType.Allow));
                    acl.AddAccessRule(new PipeAccessRule(new SecurityIdentifier(WellKnownSidType.LocalServiceSid, null), PipeAccessRights.Read, AccessControlType.Allow));
                    acl.AddAccessRule(new PipeAccessRule(new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null), PipeAccessRights.Read, AccessControlType.Allow));
                    acl.AddAccessRule(new PipeAccessRule(new SecurityIdentifier(WellKnownSidType.NetworkSid, null), PipeAccessRights.FullControl, AccessControlType.Deny));
                    pipe = new NamedPipeServerStream(Name, PipeDirection.Out, 8, PipeTransmissionMode.Byte,
                        PipeOptions.Asynchronous, 0, 65536, acl);
                    lock (gate) { if (stop.IsCancellationRequested) { pipe.Dispose(); return; } pipes.Add(pipe); }
                    await pipe.WaitForConnectionAsync(stop.Token);
                    var connected = pipe;
                    _ = Task.Run(delegate { return Send(connected); });
                }
                catch (Exception) { if (pipe != null) { lock (gate) pipes.Remove(pipe); pipe.Dispose(); } if (!stop.IsCancellationRequested) await Task.Delay(500); }
            }
        }

        async Task Send(NamedPipeServerStream pipe)
        {
            byte[] header;
            byte[] lastSent = null;
            using (var data = new MemoryStream())
            using (var writer = new BinaryWriter(data))
            {
                writer.Write(0x4D414352); writer.Write(1); writer.Write(FrameSize);
                writer.Write(Width); writer.Write(Height); writer.Write(0);
                header = data.ToArray();
            }
            try
            {
                while (!stop.IsCancellationRequested)
                {
                    byte[] frame;
                    Task nextFrame;
                    lock (gate) { frame = latest != null && clock.ElapsedMilliseconds - published < 1500 ? latest : null; nextFrame = changed.Task; }
                    if (frame != null && !Object.ReferenceEquals(frame, lastSent))
                    {
                        await pipe.WriteAsync(header, 0, header.Length, stop.Token);
                        await pipe.WriteAsync(frame, 0, frame.Length, stop.Token);
                        lastSent = frame;
                    }
                    else await nextFrame;
                }
            }
            catch (Exception) { }
            finally { lock (gate) pipes.Remove(pipe); pipe.Dispose(); }
        }
        public void Dispose()
        {
            stop.Cancel();
            lock (gate) { latest = null; changed.TrySetCanceled(); foreach (var pipe in pipes) pipe.Dispose(); pipes.Clear(); }
        }
    }

    sealed class Receiver : IDisposable
    {
        readonly string folder = AppDomain.CurrentDomain.BaseDirectory;
        readonly object processesGate = new object();
        readonly List<Process> processes = new List<Process>();
        CancellationTokenSource stop;
        Task loop;
        FrameBroker broker;
        Process camera;
        string sessionDir;
        ProcessJob job;
        long frames;
        long readCalls;
        public long ReadCalls { get { return Interlocked.Read(ref readCalls); } }
        public string Codec = "—";
        public volatile InputVideo Input;
        public volatile string AudioEndpoint;
        public volatile bool DecoderRunning;
        public int Generation;
        public long LostPackets;
        public bool HardwareDecoder { get { return useHardware; } }
        string decoderError = "";
        bool useHardware = Environment.GetEnvironmentVariable("REMOTECAM_SOFTWARE_DECODER") != "1";
        volatile bool hardwareFailed;
        readonly Queue<string> diagnostics = new Queue<string>();
        long lastFrameTicks;
        public long DecodeErrors;
        void Trace(string line)
        {
            lock (diagnostics) {
                if (diagnostics.Count == 256) diagnostics.Dequeue();
                diagnostics.Enqueue(DateTime.UtcNow.ToString("O") + " " + line);
            }
        }
        void SaveDiagnostics()
        {
            try {
                string path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "RemoteCam Desktop");
                Directory.CreateDirectory(path);
                lock (diagnostics) {
                    var lines = new List<string>();
                    lines.Add(DateTime.UtcNow.ToString("O") + " snapshot frames=" + Frames + " decodeErrors=" + Interlocked.Read(ref DecodeErrors));
                    lines.AddRange(diagnostics);
                    File.WriteAllLines(Path.Combine(path, "receiver.log"), lines);
                }
            } catch (IOException) { } catch (UnauthorizedAccessException) { }
        }
        public Action<string> Status;
        public long Frames { get { return Interlocked.Read(ref frames); } }
        public byte[] Latest { get { return broker == null ? null : broker.Latest; } }
        void Report(string value) { Trace(value); var handler = Status; if (handler != null) handler(value); }

        public static Uri PhoneUri(string value)
        {
            value = value.Trim();
            if (value.Length == 0 || value.IndexOfAny(new[] {'"', '\'', '\r', '\n', '\\'}) >= 0) throw new ArgumentException("Podaj adres telefonu, np. 192.168.1.11:8080.");
            if (!value.Contains("://")) value = "http://" + value;
            Uri uri;
            if (!Uri.TryCreate(value, UriKind.Absolute, out uri) || (uri.Scheme != "http" && uri.Scheme != "https") || uri.UserInfo.Length != 0)
                throw new ArgumentException("Podaj adres HTTP/HTTPS telefonu bez loginu i hasła.");
            var builder = new UriBuilder(uri);
            if (uri.IsDefaultPort && uri.Scheme == "http") builder.Port = 8080;
            builder.Path = "/whep"; builder.Query = ""; builder.Fragment = "";
            return builder.Uri;
        }

        static int FreePort()
        {
            var listener = new TcpListener(IPAddress.Loopback, 0);
            listener.Start(); int port = ((IPEndPoint)listener.LocalEndpoint).Port; listener.Stop(); return port;
        }
        Process Launch(string exe, string arguments, bool input)
        {
            var info = new ProcessStartInfo(Path.Combine(folder, exe), arguments);
            info.UseShellExecute = false; info.CreateNoWindow = true;
            info.RedirectStandardOutput = true; info.RedirectStandardError = true;
            info.RedirectStandardInput = input; info.WorkingDirectory = folder;
            if (exe == "go2rtc.exe") info.EnvironmentVariables["REMOTECAM_RTP_REPAIR"] = "1";
            var process = new Process(); process.StartInfo = info;
            process.Start();
            try { job.Add(process); }
            catch { try { process.Kill(); } catch (Exception) { } process.Dispose(); throw; }
            lock (processesGate) processes.Add(process);
            return process;
        }
        void DrainErrors(Process p, bool codec)
        {
            bool inputSection = false;
            long previousLoss = 0;
            p.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs e)
            {
                if (e.Data == null) return;
                if (!e.Data.StartsWith("frame=")) Trace(e.Data);
                if (!codec && e.Data.Contains("[repair]")) {
                    var loss = System.Text.RegularExpressions.Regex.Match(e.Data, @"\blost=(\d+)");
                    long count;
                    if (loss.Success && Int64.TryParse(loss.Groups[1].Value, out count)) {
                        Interlocked.Add(ref LostPackets, Math.Max(0, count - previousLoss)); previousLoss = count;
                    }
                }
                if (codec && e.Data.StartsWith("Input #")) inputSection = true;
                if (codec && e.Data.StartsWith("Output #")) inputSection = false;
                if (codec && (e.Data.Contains("corrupt") || e.Data.Contains("error while decoding") || e.Data.Contains("Could not find ref")))
                    Interlocked.Increment(ref DecodeErrors);
                if (codec && (e.Data.Contains("Device setup failed") || e.Data.Contains("No device available") ||
                    e.Data.Contains("Failed setup for format d3d11") || e.Data.Contains("Failed to create D3D11") ||
                    e.Data.Contains("Invalid output format") || e.Data.Contains("Error reinitializing filters"))) hardwareFailed = true;
                if (codec && (e.Data.IndexOf("error", StringComparison.OrdinalIgnoreCase) >= 0 || e.Data.Contains("Option "))) decoderError = e.Data;
                if (codec && e.Data.Contains("Video: "))
                {
                    if (inputSection) {
                        var input = InputVideo.Parse(e.Data);
                        if (input != null) { Input = input; Codec = input.Codec; }
                    }
                }
            };
            p.BeginErrorReadLine();
        }

        public async Task Start(string address, bool createCamera)
        {
            Uri phone = PhoneUri(address);
            foreach (string binary in new[] {"go2rtc.exe", "ffmpeg.exe", "RemoteCamHost.exe"})
                if (!File.Exists(Path.Combine(folder, binary))) throw new FileNotFoundException("Brak składnika aplikacji: " + binary);
            stop = new CancellationTokenSource();
            job = new ProcessJob();
            broker = new FrameBroker(); broker.Start();
            sessionDir = Path.Combine(Path.GetTempPath(), "RemoteCam-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(sessionDir);
            try
            {
                if (createCamera)
                {
                    Report("Uruchamianie kamery Windows…");
                    camera = Launch("RemoteCamHost.exe", "--pipe \\\\.\\pipe\\" + broker.Name, true);
                    Task<string> ready = camera.StandardOutput.ReadLineAsync();
                    if (await Task.WhenAny(ready, Task.Delay(15000, stop.Token)) != ready)
                        throw new IOException("Windows nie uruchomił kamery w ciągu 15 s. Sprawdź instalację i uprawnienia aparatu.");
                    string line = await ready;
                    if (line == null || !line.StartsWith("READY"))
                    {
                        string details = await camera.StandardError.ReadToEndAsync();
                        string help = details.Contains("80040154") ? "Użyj przycisku „Zainstaluj kamerę”. " : "Zakończ poprzednią sesję RemoteCam i spróbuj połączyć ponownie. ";
                        throw new IOException("Kamera Windows nie wystartowała. " + help + details);
                    }
                    DrainErrors(camera, false);
                    Report("Kamera RemoteCam jest dostępna w Windows.");
                }
                loop = Task.Run(delegate { return ReceiveLoop(phone, stop.Token); });
            }
            catch { await Stop(); throw; }
        }

        async Task ReceiveLoop(Uri phone, CancellationToken token)
        {
            while (!token.IsCancellationRequested)
            {
                Process relay = null, decoder = null;
                CancellationTokenSource attemptStop = null;
                Task monitor = null;
                try
                {
                    DecoderRunning = false; Input = null; Interlocked.Increment(ref Generation);
                    int api = FreePort(), rtsp = FreePort();
                    while (rtsp == api) rtsp = FreePort();
                    string config = Path.Combine(sessionDir, "go2rtc.yaml");
                    File.WriteAllText(config, "api:\n  listen: '127.0.0.1:" + api + "'\nrtsp:\n  listen: '127.0.0.1:" + rtsp + "'\nwebrtc:\n  listen: ':0'\nstreams:\n  phone:\n    - 'webrtc:" + phone.AbsoluteUri + "'\n");
                    relay = Launch("go2rtc.exe", "-config \"" + config + "\"", false);
                    DrainErrors(relay, false); relay.BeginOutputReadLine();
                    using (var http = new HttpClient())
                    {
                        http.Timeout = TimeSpan.FromSeconds(1);
                        bool available = false;
                        for (int attempt = 0; attempt < 25 && !available; ++attempt)
                        {
                            token.ThrowIfCancellationRequested();
                            if (relay.HasExited) throw new IOException("Odbiornik nie wystartował. Sprawdź wolne porty lokalne.");
                            try { using (var response = await http.GetAsync("http://127.0.0.1:" + api + "/api/streams", token)) available = response.IsSuccessStatusCode; }
                            catch (HttpRequestException) { }
                            catch (TaskCanceledException) { token.ThrowIfCancellationRequested(); }
                            if (!available) await Task.Delay(200, token);
                        }
                        if (!available) throw new IOException("Odbiornik lokalny nie odpowiada.");
                    }
                    Report("Łączenie z telefonem… Włącz Stream i tryb WebRTC.");
                    AudioEndpoint = "rtsp://127.0.0.1:" + rtsp + "/phone";
                    decoderError = "";
                    hardwareFailed = false;
                    // Preserve the probed keyframe/parameter sets; nobuffer discarded
                    // them. RTP is repaired before RTSP, which carries video only.
                    string acceleration = useHardware ? "-hwaccel d3d11va -hwaccel_output_format d3d11 -threads 1 " : "-threads 4 ";
                    string download = useHardware ? "hwdownload,format=nv12," : "";
                    string args = "-hide_banner -loglevel info -nostdin -rtsp_transport tcp -allowed_media_types video -timeout 5000000 " + acceleration + "-probesize 1000000 -analyzeduration 1000000 -i rtsp://127.0.0.1:" + rtsp + "/phone -an -sn -dn -filter_threads 1 -vf \"" + download + "scale=1920:1080:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1\" -pix_fmt nv12 -threads:v 1 -fps_mode passthrough -f rawvideo pipe:1";
                    Trace("decoder=" + (useHardware ? "D3D11VA GPU" : "CPU 4 threads"));
                    decoder = Launch("ffmpeg.exe", args, false);
                    DrainErrors(decoder, true);
                    Interlocked.Exchange(ref lastFrameTicks, 0);
                    attemptStop = CancellationTokenSource.CreateLinkedTokenSource(token);
                    monitor = MonitorPipeline(api, decoder, attemptStop.Token);
                    var output = decoder.StandardOutput.BaseStream;
                    bool firstFrame = true;
                    while (!token.IsCancellationRequested)
                    {
                        byte[] frame = new byte[FrameBroker.FrameSize];
                        int offset = 0;
                        while (offset < frame.Length)
                        {
                            // This loop already runs on a worker. Anonymous pipes use
                            // small reads; scheduling a Task and an uncancelled timer
                            // for every chunk caused thousands of pending timers.
                            // The per-pipeline monitor and Stop() kill the decoder to
                            // unblock this read even if packets arrive without frames.
                            int count = output.Read(frame, offset, frame.Length - offset);
                            Interlocked.Increment(ref readCalls);
                            if (count == 0) throw new IOException("Połączenie wideo zostało przerwane. " + decoderError);
                            offset += count;
                        }
                        broker.Publish(frame);
                        DecoderRunning = true;
                        Interlocked.Exchange(ref lastFrameTicks, Stopwatch.GetTimestamp());
                        Interlocked.Increment(ref frames);
                        if (firstFrame) { Report("Połączono · " + Codec + " · " + (useHardware ? "GPU" : "CPU") + " · wyjście 1920 × 1080 / 30 fps"); firstFrame = false; }
                    }
                }
                catch (Exception e) {
                    // EOF can precede asynchronous stderr delivery. Drain an exited
                    // process before deciding whether GPU initialization failed.
                    if (decoder != null) { try { if (decoder.HasExited) decoder.WaitForExit(); } catch (InvalidOperationException) { } }
                    if (useHardware && hardwareFailed) { useHardware = false; Trace("GPU unavailable — switching to software decoder."); }
                    if (!token.IsCancellationRequested) Report(e.Message + " Ponawiam za 2 s…");
                }
                finally {
                    AudioEndpoint = null;
                    DecoderRunning = false;
                    if (attemptStop != null) {
                        attemptStop.Cancel();
                        if (monitor != null) { try { await monitor; } catch (OperationCanceledException) { } }
                        attemptStop.Dispose();
                    }
                    broker.Clear(); EndProcess(decoder); EndProcess(relay); SaveDiagnostics();
                }
                if (!token.IsCancellationRequested) { try { await Task.Delay(2000, token); } catch (OperationCanceledException) { } }
            }
        }
        public sealed class SourceSnapshot { public int id { get; set; } }
        public sealed class StreamSnapshot { public SourceSnapshot[] producers { get; set; } }
        async Task MonitorPipeline(int api, Process decoder, CancellationToken token)
        {
            long started = Stopwatch.GetTimestamp();
            int producer = 0;
            long lastDiagnostic = started;
            using (var http = new HttpClient()) {
                http.Timeout = TimeSpan.FromSeconds(1);
                var json = new JavaScriptSerializer();
                while (!token.IsCancellationRequested) {
                    await Task.Delay(500, token);
                    long last = Interlocked.Read(ref lastFrameTicks);
                    double seconds = (Stopwatch.GetTimestamp() - (last == 0 ? started : last)) / (double)Stopwatch.Frequency;
                    string reason = seconds > (last == 0 ? 12 : 4) ? "Brak nowych klatek — odnawiam odbiór." : null;
                    try {
                        using (var response = await http.GetAsync("http://127.0.0.1:" + api + "/api/streams", token)) {
                            if (response.IsSuccessStatusCode) {
                                var streams = json.Deserialize<Dictionary<string, StreamSnapshot>>(await response.Content.ReadAsStringAsync());
                                StreamSnapshot stream;
                                if (streams != null && streams.TryGetValue("phone", out stream) && stream.producers != null && stream.producers.Length > 0) {
                                    int current = stream.producers[0].id;
                                    if (producer != 0 && current != producer) reason = "Telefon zmienił strumień — odnawiam odbiór.";
                                    producer = current;
                                }
                            }
                        }
                    } catch (HttpRequestException) { }
                    catch (TaskCanceledException) { token.ThrowIfCancellationRequested(); }
                    catch (ArgumentException) { }
                    catch (InvalidOperationException) { }
                    if ((Stopwatch.GetTimestamp() - lastDiagnostic) / (double)Stopwatch.Frequency >= 5) {
                        SaveDiagnostics(); lastDiagnostic = Stopwatch.GetTimestamp();
                    }
                    if (reason != null) {
                        Report(reason);
                        try { if (!decoder.HasExited) decoder.Kill(); } catch (InvalidOperationException) { }
                        return;
                    }
                }
            }
        }
        void EndProcess(Process p)
        {
            if (p == null) return;
            lock (processesGate) processes.Remove(p);
            try { if (!p.HasExited) p.Kill(); p.WaitForExit(2000); } catch (InvalidOperationException) { }
            p.Dispose();
        }
        public async Task Stop()
        {
            if (stop == null) return;
            stop.Cancel();
            if (camera != null)
            {
                try { camera.StandardInput.WriteLine(); camera.StandardInput.Close(); } catch (Exception) { }
                await Task.Run(delegate { try { camera.WaitForExit(3000); } catch (Exception) { } });
            }
            lock (processesGate)
                foreach (var p in processes) { try { if (!p.HasExited) p.Kill(); } catch (Exception) { } }
            if (loop != null) { try { await loop; } catch (OperationCanceledException) { } }
            EndProcess(camera); camera = null;
            if (broker != null) broker.Dispose();
            if (sessionDir != null && Directory.Exists(sessionDir))
            {
                // This directory was created with a random name by this Receiver only.
                string config = Path.Combine(sessionDir, "go2rtc.yaml");
                if (File.Exists(config)) File.Delete(config);
                Directory.Delete(sessionDir, false);
            }
            stop.Dispose(); stop = null;
            if (job != null) job.Dispose(); job = null;
            Report("Rozłączono. Kamera została wyłączona.");
            Trace("frames=" + Frames + " decodeErrors=" + Interlocked.Read(ref DecodeErrors));
            SaveDiagnostics();
        }
        public void Dispose() { Stop().GetAwaiter().GetResult(); }
    }

    // Closing the desktop process also closes this handle, so its decoder, relay
    // and camera host cannot remain running after a crash or forced termination.
    sealed class ProcessJob : IDisposable
    {
        [StructLayout(LayoutKind.Sequential)] struct BasicLimits
        {
            public long ProcessTime, JobTime;
            public uint Flags;
            public UIntPtr MinWorkingSet, MaxWorkingSet;
            public uint ActiveProcesses;
            public UIntPtr Affinity;
            public uint PriorityClass, SchedulingClass;
        }
        [StructLayout(LayoutKind.Sequential)] struct IoCounters
        { public ulong ReadOps, WriteOps, OtherOps, ReadBytes, WriteBytes, OtherBytes; }
        [StructLayout(LayoutKind.Sequential)] struct ExtendedLimits
        {
            public BasicLimits Basic;
            public IoCounters Io;
            public UIntPtr ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory;
        }
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)] static extern IntPtr CreateJobObject(IntPtr security, string name);
        [DllImport("kernel32.dll", SetLastError = true)] static extern bool SetInformationJobObject(IntPtr job, int kind, ref ExtendedLimits data, uint size);
        [DllImport("kernel32.dll", SetLastError = true)] static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        IntPtr handle;
        public ProcessJob()
        {
            handle = CreateJobObject(IntPtr.Zero, null);
            if (handle == IntPtr.Zero) throw new System.ComponentModel.Win32Exception();
            var limits = new ExtendedLimits(); limits.Basic.Flags = 0x2000;
            if (!SetInformationJobObject(handle, 9, ref limits, (uint)Marshal.SizeOf(limits)))
            { int error = Marshal.GetLastWin32Error(); Dispose(); throw new System.ComponentModel.Win32Exception(error); }
        }
        public void Add(Process p) { if (!AssignProcessToJobObject(handle, p.Handle)) throw new System.ComponentModel.Win32Exception(); }
        public void Dispose() { if (handle != IntPtr.Zero) { CloseHandle(handle); handle = IntPtr.Zero; } }
    }

    static class Preview
    {
        static byte Clamp(int value) { return (byte)Math.Max(0, Math.Min(255, value)); }
        public static unsafe Bitmap Create(byte[] nv12)
        {
            const int width = 640, height = 360;
            var bitmap = new Bitmap(width, height, PixelFormat.Format32bppArgb);
            var data = bitmap.LockBits(new Rectangle(0, 0, width, height), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            for (int y = 0; y < height; ++y)
            {
                byte* row = (byte*)data.Scan0 + y * data.Stride;
                for (int x = 0; x < width; ++x)
                {
                    int sx = x * 3, sy = y * 3;
                    int yy = nv12[sy * 1920 + sx] - 16;
                    int chroma = 1920 * 1080 + (sy / 2) * 1920 + (sx & ~1);
                    int u = nv12[chroma] - 128, v = nv12[chroma + 1] - 128;
                    row[x * 4] = Clamp((298 * yy + 516 * u + 128) >> 8);
                    row[x * 4 + 1] = Clamp((298 * yy - 100 * u - 208 * v + 128) >> 8);
                    row[x * 4 + 2] = Clamp((298 * yy + 409 * v + 128) >> 8);
                    row[x * 4 + 3] = 255;
                }
            }
            bitmap.UnlockBits(data); return bitmap;
        }
    }

    sealed class MainWindow : Form
    {
        [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr window, int attribute, ref int value, int size);
        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            int dark = 1, caption = 28 | (24 << 8) | (21 << 16), ink = 255 | (225 << 8) | (90 << 16);
            DwmSetWindowAttribute(Handle, 20, ref dark, sizeof(int));
            DwmSetWindowAttribute(Handle, 35, ref caption, sizeof(int));
            DwmSetWindowAttribute(Handle, 36, ref ink, sizeof(int));
        }
        readonly TextBox address = new TextBox();
        readonly Button connect = new Button(), install = new Button();
        readonly Label status = new Label(), metrics = new Label();
        readonly PictureBox preview = new PictureBox();
        readonly CheckBox previewEnabled = new CheckBox();
        readonly Label previewNotice = new Label();
        readonly NotifyIcon tray = new NotifyIcon();
        readonly bool diagnosticMode;
        bool inTray, trayNoticeShown;
        internal bool exitRequested;
        long previewConversions;
        readonly Panel hardwarePage = new Panel();
        readonly Button hardwareTab = new Button();
        AudioPanel audioPage;
        readonly Label hardwareText = new Label(), streamText = new Label(), adviceText = new Label();
        readonly StreamAdvice advice = new StreamAdvice();
        long previousErrors, previousLoss;
        readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();
        readonly string settings = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "RemoteCam", "desktop-address.txt");
        Receiver receiver;
        bool busy, closing;
        bool renderingPreview;
        byte[] lastPreviewFrame;
        long presentedFrames, previousPresented, previousReceived;
        readonly Stopwatch metricsClock = Stopwatch.StartNew();
        long previousMetricTime;
        public MainWindow(bool diagnosticMode = false)
        {
            this.diagnosticMode = diagnosticMode;
            Text = "RemoteCam Desktop · wersja testowa " + Brand.Version;
            Icon = Brand.LoadIcon(); ShowIcon = true;
            ClientSize = new Size(900, 700); MinimumSize = new Size(740, 620);
            StartPosition = FormStartPosition.CenterScreen;
            // Website accent (#ffe15a), paired with the requested warm dark brown.
            var background = Color.FromArgb(28, 24, 21);
            var panel = Color.FromArgb(40, 34, 29);
            var accent = Color.FromArgb(255, 225, 90);
            var ink = Color.FromArgb(243, 242, 237);
            var muted = Color.FromArgb(181, 171, 157);
            BackColor = background; ForeColor = ink;
            Font = new Font("Segoe UI", 10);
            var title = new Panel { Dock = DockStyle.Top, Height = 94 };
            var logo = new PictureBox { Image = Brand.LoadLogo(), SizeMode = PictureBoxSizeMode.Zoom, Left = 22, Top = 15, Width = 64, Height = 64, AccessibleName = "Logo RemoteCam" };
            var wordmark = new Label { Text = "RemoteCam", AutoSize = true, Left = 101, Top = 11, ForeColor = ink, Font = new Font("Segoe UI", 25, FontStyle.Bold) };
            var tagline = new Label { Text = "Twoja kamera. Twój komputer.", AutoSize = true, Left = 104, Top = 60, ForeColor = muted };
            var badge = new Label { Text = "DESKTOP  /  " + Brand.Version + " TEST", AutoSize = true, ForeColor = accent, Font = new Font("Segoe UI", 9, FontStyle.Bold), Top = 27, Anchor = AnchorStyles.Top | AnchorStyles.Right };
            title.Controls.AddRange(new Control[] {logo, wordmark, tagline, badge});
            title.Resize += delegate { badge.Left = title.ClientSize.Width - badge.Width - 24; };
            var help = new Label { Text = "Włącz Stream w RemoteCam na telefonie i wybierz H.264 lub H.265 + WebRTC.", ForeColor = muted, Dock = DockStyle.Top, Height = 40, Padding = new Padding(22, 5, 0, 0) };
            var connection = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 54, Padding = new Padding(22, 5, 0, 0) };
            address.Width = 330; address.Text = File.Exists(settings) ? File.ReadAllText(settings) : "192.168.1.11:8080";
            address.BackColor = panel; address.ForeColor = ink; address.BorderStyle = BorderStyle.FixedSingle;
            connect.Text = "Połącz"; connect.Width = 130; connect.Height = 32;
            install.Text = "Zainstaluj kamerę"; install.Width = 170; install.Height = 32;
            foreach (Button b in new[] {connect, install}) { b.FlatStyle = FlatStyle.Flat; b.Cursor = Cursors.Hand; b.FlatAppearance.BorderColor = Color.FromArgb(86, 73, 51); }
            connect.BackColor = accent; connect.ForeColor = background; connect.Font = new Font(Font, FontStyle.Bold);
            connect.FlatAppearance.BorderColor = accent; connect.FlatAppearance.MouseOverBackColor = Color.FromArgb(255, 233, 129);
            install.BackColor = panel; install.ForeColor = ink; install.FlatAppearance.MouseOverBackColor = Color.FromArgb(57, 47, 37);
            connection.Controls.AddRange(new Control[] {address, connect, install});
            preview.Dock = DockStyle.Fill; preview.BackColor = Color.FromArgb(17, 14, 12); preview.SizeMode = PictureBoxSizeMode.Zoom;
            previewNotice.Dock = DockStyle.Fill; previewNotice.TextAlign = ContentAlignment.MiddleCenter; previewNotice.ForeColor = muted;
            previewNotice.Text = "Podgląd lokalny wyłączony.\nObraz nadal trafia do kamery Windows.";
            preview.Controls.Add(previewNotice);
            var footer = new Panel { Dock = DockStyle.Bottom, Height = 170, Padding = new Padding(20), BackColor = panel };
            string registered = Microsoft.Win32.Registry.GetValue(@"HKEY_LOCAL_MACHINE\SOFTWARE\Classes\CLSID\{D168A389-283B-4AD8-ACB4-1CC943368FA0}\InprocServer32", "", null) as string;
            status.Text = registered != null && File.Exists(registered) ? "Kamera zainstalowana. Włącz Stream na telefonie i kliknij Połącz." : "Gotowy. Przy pierwszym uruchomieniu zainstaluj kamerę."; status.Dock = DockStyle.Top; status.Height = 45;
            metrics.Dock = DockStyle.Top; metrics.Height = 25; metrics.ForeColor = accent;
            var instructions = new Label { Text = "OBS: wybierz kamerę RemoteCam. Audio telefonu włączysz w zakładce Mikrofon.\nKrzyżyk chowa do traya. Aby zatrzymać transmisję, wybierz Zakończ z menu ikony.", Dock = DockStyle.Bottom, Height = 52, ForeColor = muted };
            footer.Controls.Add(instructions); footer.Controls.Add(metrics); footer.Controls.Add(status);
            var content = new Panel { Dock = DockStyle.Fill };
            content.Controls.Add(preview);
            audioPage = new AudioPanel(() => receiver) { Dock = DockStyle.Fill, Visible = false };
            content.Controls.Add(audioPage);
            hardwarePage.Dock = DockStyle.Fill; hardwarePage.BackColor = background; hardwarePage.AutoScroll = true;
            var cards = new TableLayoutPanel { Dock = DockStyle.Top, AutoSize = true, ColumnCount = 1, Padding = new Padding(22, 12, 22, 20) };
            var equipmentTitle = new Label { Text = "TWÓJ KOMPUTER", ForeColor = accent, AutoSize = true, Font = new Font(Font, FontStyle.Bold) };
            hardwareText.Text = "Odczytuję CPU, GPU i pamięć…";
            var adviceTitle = new Label { Text = "ZALECENIA DLA STRUMIENIA", ForeColor = accent, AutoSize = true, Font = new Font(Font, FontStyle.Bold) };
            var note = new Label { Text = "Kamera Windows udostępnia 1920 × 1080 / 30 kl./s. Wyższa rozdzielczość telefonu nadal wymaga zdekodowania całego obrazu przed zmniejszeniem.\n\nLista GPU nie potwierdza obsługi danego kodeka. Potwierdzeniem jest działający odbiór GPU. Zalecenie startowe jest ostrożnym punktem wyjścia; 4K wymaga pomiaru. Ustawienia zmień w telefonie.", ForeColor = muted };
            foreach (Label label in new[] {equipmentTitle, hardwareText, streamText, adviceTitle, adviceText, note}) {
                label.AutoSize = true; label.Margin = new Padding(0, 0, 0, 14); cards.Controls.Add(label);
            }
            cards.Resize += delegate { foreach (Control label in cards.Controls) label.MaximumSize = new Size(Math.Max(200, cards.ClientSize.Width - 48), 0); };
            hardwarePage.Controls.Add(cards); content.Controls.Add(hardwarePage); hardwarePage.Visible = false;
            var tabs = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 43, Padding = new Padding(22, 2, 0, 0) };
            var videoTab = new Button { Text = "Podgląd", Width = 120, Height = 32 };
            hardwareTab.Text = "Sprzęt i jakość"; hardwareTab.Width = 165; hardwareTab.Height = 32;
            var audioTab = new Button { Text = "Mikrofon", Width = 130, Height = 32 };
            foreach (Button tab in new[] {videoTab, hardwareTab, audioTab}) { tab.FlatStyle = FlatStyle.Flat; tab.FlatAppearance.BorderColor = Color.FromArgb(86, 73, 51); tab.Cursor = Cursors.Hand; }
            Action<bool> selectTab = delegate(bool hardware) {
                preview.Visible = !hardware; hardwarePage.Visible = hardware; if (hardware) hardwarePage.BringToFront();
                audioPage.Visible = false; audioTab.BackColor = panel; audioTab.ForeColor = ink;
                footer.Height = hardware ? 90 : 170; instructions.Visible = !hardware;
                videoTab.BackColor = hardware ? panel : accent; videoTab.ForeColor = hardware ? ink : background;
                hardwareTab.BackColor = hardware ? accent : panel; hardwareTab.ForeColor = hardware ? background : ink;
            };
            videoTab.Click += delegate { selectTab(false); }; hardwareTab.Click += delegate { selectTab(true); };
            audioTab.Click += delegate {
                selectTab(false); preview.Visible = false; audioPage.Visible = true; audioPage.BringToFront();
                footer.Height = 90; instructions.Visible = false; videoTab.BackColor = panel; videoTab.ForeColor = ink;
                audioTab.BackColor = accent; audioTab.ForeColor = background; audioPage.RefreshDevices();
            };
            previewEnabled.Text = "Podgląd lokalny"; previewEnabled.Checked = true; previewEnabled.AutoSize = true; previewEnabled.Margin = new Padding(14, 7, 0, 0);
            previewEnabled.CheckedChanged += delegate { UpdatePreviewMode(); };
            selectTab(false); tabs.Controls.AddRange(new Control[] { videoTab, hardwareTab, audioTab, previewEnabled });
            Controls.Add(content); Controls.Add(footer); Controls.Add(tabs); Controls.Add(connection); Controls.Add(help); Controls.Add(title);
            UpdateHardware(null);
            Shown += async delegate {
                var probe = Task.Run(() => HardwareProfile.Read());
                if (await Task.WhenAny(probe, Task.Delay(10000)) != probe) {
                    if (!IsDisposed) hardwareText.Text = "Windows nie odpowiedział na odczyt sprzętu. Zalecenia na podstawie odbioru są nadal dostępne.";
                    return;
                }
                var hardware = await probe;
                if (!IsDisposed) hardwareText.Text = hardware.Description;
            };
            connect.Click += async delegate { await Toggle(); };
            install.Click += async delegate { await Install(); };
            // Poll more often than the 30 fps input to avoid WinForms timer
            // granularity turning a nominal 33 ms interval into roughly 20 fps.
            // Unchanged frames are skipped, with at most one conversion in flight.
            timer.Interval = 15; timer.Tick += async delegate { await RefreshPreview(); }; timer.Start();
            if (!diagnosticMode) {
                tray.Icon = Icon; tray.Text = "RemoteCam Desktop"; tray.Visible = true;
                var menu = new ContextMenuStrip();
                menu.Items.Add("Pokaż RemoteCam", null, delegate { RestoreFromTray(); });
                var trayMute = new ToolStripMenuItem("Wycisz mikrofon") { CheckOnClick = true };
                trayMute.Click += delegate { audioPage.MicrophoneMuted = trayMute.Checked; };
                menu.Items.Add(trayMute);
                var disconnect = menu.Items.Add("Rozłącz", null, async delegate { if (receiver != null) await Toggle(); });
                menu.Items.Add(new ToolStripSeparator());
                menu.Items.Add("Zakończ — zatrzymaj transmisję", null, delegate { exitRequested = true; if (!busy) Close(); });
                menu.Opening += delegate { trayMute.Checked = audioPage.MicrophoneMuted; trayMute.Enabled = audioPage.AudioActive; disconnect.Enabled = receiver != null && !busy; };
                tray.ContextMenuStrip = menu; tray.DoubleClick += delegate { RestoreFromTray(); };
            }
            VisibleChanged += delegate { UpdatePreviewMode(); };
            preview.VisibleChanged += delegate { UpdatePreviewMode(); };
            Resize += delegate {
                if (!diagnosticMode && WindowState == FormWindowState.Minimized && !closing) HideToTray();
                else UpdatePreviewMode();
            };
            FormClosed += delegate { tray.Visible = false; if (tray.ContextMenuStrip != null) tray.ContextMenuStrip.Dispose(); tray.Dispose(); timer.Dispose(); logo.Image.Dispose(); Icon.Dispose(); ClearPreview(); };
            FormClosing += async delegate(object sender, FormClosingEventArgs e)
            {
                if (closing) return;
                if (e.CloseReason == CloseReason.WindowsShutDown || e.CloseReason == CloseReason.TaskManagerClosing) {
                    // Do not block logout/shutdown. OS process teardown closes our
                    // kill-on-close jobs and terminates the owned media children.
                    closing = true; tray.Visible = false; timer.Stop(); return;
                }
                e.Cancel = true;
                if (!diagnosticMode && !exitRequested && e.CloseReason == CloseReason.UserClosing) { HideToTray(); return; }
                if (busy) return;
                busy = true; connect.Enabled = false;
                await audioPage.StopAudio();
                if (receiver != null) { await receiver.Stop(); receiver.Dispose(); receiver = null; }
                closing = true; timer.Stop(); Close();
            };
        }
        bool PreviewActive { get { return !closing && !inTray && Visible && WindowState != FormWindowState.Minimized && preview.Visible && previewEnabled.Checked; } }
        void ClearPreview()
        {
            Image old = preview.Image; preview.Image = null; if (old != null) old.Dispose(); lastPreviewFrame = null;
        }
        void UpdatePreviewMode()
        {
            if (closing || IsDisposed) return;
            bool active = PreviewActive;
            timer.Interval = active ? 15 : 1000;
            previewNotice.Visible = !previewEnabled.Checked;
            if (!active) ClearPreview();
        }
        void HideToTray()
        {
            inTray = true; ShowInTaskbar = false; Hide(); UpdatePreviewMode();
            if (!trayNoticeShown && !diagnosticMode) {
                trayNoticeShown = true;
                tray.ShowBalloonTip(2500, "RemoteCam działa w tle", "Transmisja pozostaje aktywna. Podgląd jest wyłączony. Zakończ aplikację z menu ikony.", ToolTipIcon.Info);
            }
        }
        void RestoreFromTray()
        {
            inTray = false; ShowInTaskbar = !diagnosticMode;
            WindowState = FormWindowState.Normal; Show(); Activate(); UpdatePreviewMode();
        }
        void Report(string value)
        {
            if (!IsDisposed && IsHandleCreated) BeginInvoke((Action)delegate { status.Text = value; });
        }
        public void ShowHardwareForRender()
        {
            hardwareText.Text = HardwareProfile.Read().Description;
            hardwareTab.PerformClick();
        }
        void UpdateHardware(Receiver active)
        {
            if (active == null) {
                advice.Reset();
                streamText.Text = "Dekoder: nieaktywny · obsługę GPU sprawdzimy po połączeniu.";
                adviceText.Text = "START: H.264 · 1920 × 1080 · 30 kl./s.\nJeśli odbiór używa CPU i przycina, zacznij od 1280 × 720 / 30 kl./s. Połącz się, aby sprawdzić profil przez 30 s.";
                return;
            }
            var input = active.Input;
            streamText.Text = "Dekoder: " + (active.DecoderRunning ? (active.HardwareDecoder ? "GPU · D3D11VA (aktywny)" : "CPU · dekodowanie programowe (4 wątki)") : "oczekiwanie na obraz") +
                "\nTelefon: " + (input == null ? "odczytuję parametry…" : input.Description);
            string baseline = active.HardwareDecoder ? "ZALECENIE STARTOWE: 1920 × 1080 / 30 kl./s, H.264." : "ZALECENIE STARTOWE DLA CPU: 1280 × 720 / 30 kl./s, H.264. Jeśli pomiar jest płynny, sprawdź 1920 × 1080.";
            adviceText.Text = baseline + "\n\n" + (advice.Result.Length > 0 ? advice.Result : "Pomiar bieżącego profilu: " + Math.Min(30, (int)advice.Seconds) + "/30 s po rozgrzaniu odbioru. Zmiana strumienia rozpoczyna ocenę od nowa.");
        }
        public async Task<int> MeasurePreview(string phone, int seconds)
        {
            string resultPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "preview-result.txt");
            int connections = 0;
            try
            {
                receiver = new Receiver(); receiver.Status = delegate(string message) { if (message.StartsWith("Połączono")) Interlocked.Increment(ref connections); Report(message); };
                await receiver.Start(phone, true);
                var ready = Stopwatch.StartNew();
                while (presentedFrames == 0 && ready.ElapsedMilliseconds < 15000) await Task.Delay(50);
                if (presentedFrames == 0) throw new IOException("No preview frame arrived within 15 seconds.");
                long firstReceived = receiver.Frames, firstPresented = presentedFrames;
                long firstReads = receiver.ReadCalls;
                TimeSpan cpu = Process.GetCurrentProcess().TotalProcessorTime;
                var measure = Stopwatch.StartNew();
                await Task.Delay(Math.Max(3, seconds) * 1000);
                double elapsed = measure.Elapsed.TotalSeconds;
                double receiveFps = (receiver.Frames - firstReceived) / elapsed;
                double previewFps = (presentedFrames - firstPresented) / elapsed;
                File.WriteAllText(resultPath, String.Format(System.Globalization.CultureInfo.InvariantCulture,
                    "codec={0}\nseconds={1:F2}\nreceiveFps={2:F2}\npreviewFps={3:F2}\nprocessCpuSeconds={4:F2}\npipeReads={5}\nconnections={6}\n",
                    receiver.Codec, elapsed, receiveFps, previewFps, (Process.GetCurrentProcess().TotalProcessorTime - cpu).TotalSeconds, receiver.ReadCalls - firstReads, connections));
                File.AppendAllText(resultPath, "input=" + (receiver.Input == null ? "unknown" : receiver.Input.Description) + "\ndecoder=" + (receiver.HardwareDecoder ? "GPU" : "CPU") + "\nadvice=" + advice.Result + "\n");
                return receiveFps >= 25 && previewFps >= 25 ? 0 : 2;
            }
            catch (Exception e) { File.WriteAllText(resultPath, e.ToString()); return 1; }
            finally { if (receiver != null) { await receiver.Stop(); receiver.Dispose(); receiver = null; } }
        }
        public async Task<int> MeasureTray(string phone, string resultPath)
        {
            var log = new StringBuilder();
            try {
                trayNoticeShown = true;
                address.Text = phone; await Toggle();
                var ready = Stopwatch.StartNew();
                while (presentedFrames == 0 && ready.ElapsedMilliseconds < 15000) await Task.Delay(50);
                if (receiver == null || presentedFrames == 0) throw new IOException("No video for tray test.");
                int generation = receiver.Generation;
                var cables = CableDevice.List();
                AudioModule testAudio = null;
                try {
                    if (cables.Count == 1) { testAudio = new AudioModule(); testAudio.Start(receiver, cables[0].Id); await Task.Delay(4000); }
                    Close(); // exercise the actual user-close path, not just Hide()
                    await Task.Delay(250);
                    long before = receiver.Frames, conversions = previewConversions;
                    long audioBefore = testAudio == null ? 0 : Interlocked.Read(ref testAudio.ReceivedBytes);
                    if (!inTray || Visible || IsDisposed || timer.Interval != 1000) throw new Exception("Close did not suspend preview in tray.");
                    var probeInfo = new ProcessStartInfo(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "RemoteCamHost.exe"), "--probe") {
                        UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true
                    };
                    using (var probe = Process.Start(probeInfo)) {
                        var stdout = probe.StandardOutput.ReadToEndAsync(); var stderr = probe.StandardError.ReadToEndAsync();
                        if (!await Task.Run(() => probe.WaitForExit(15000))) { probe.Kill(); throw new Exception("Camera probe timed out."); }
                        log.AppendLine("cameraProbeExit=" + probe.ExitCode); log.AppendLine(await stdout); log.AppendLine(await stderr);
                        if (probe.ExitCode != 0) throw new Exception("System camera failed while in tray.");
                    }
                    await Task.Delay(5000);
                    long hiddenFrames = receiver.Frames - before;
                    long hiddenAudio = testAudio == null ? 0 : Interlocked.Read(ref testAudio.ReceivedBytes) - audioBefore;
                    log.AppendLine("hiddenVideoFrames=" + hiddenFrames + " hiddenAudioBytes=" + hiddenAudio + " hiddenPreviewConversions=" + (previewConversions - conversions));
                    if (hiddenFrames < 60 || previewConversions != conversions) throw new Exception("Tray did not preserve video with zero preview conversions.");
                    if (testAudio != null && hiddenAudio == 0) throw new Exception("No audio while in tray.");
                    RestoreFromTray(); ShowInTaskbar = false;
                    long resumed = presentedFrames; await Task.Delay(1500);
                    if (presentedFrames <= resumed) throw new Exception("Preview did not resume.");
                    previewEnabled.Checked = false; await Task.Delay(100);
                    conversions = previewConversions; await Task.Delay(1500);
                    if (previewConversions != conversions) throw new Exception("Manual preview disable failed.");
                    previewEnabled.Checked = true;
                    WindowState = FormWindowState.Minimized; await Task.Delay(250);
                    if (!inTray || Visible || PreviewActive) throw new Exception("Minimize did not enter tray.");
                    log.AppendLine("restoredPreview=true manualDisable=true minimizedToTray=true receiverRestarted=" + (generation != receiver.Generation));
                } finally { if (testAudio != null) await testAudio.Stop(); }
                return 0;
            } catch (Exception e) { log.AppendLine(e.ToString()); return 1; }
            finally {
                File.WriteAllText(resultPath, log.ToString());
            }
        }
        async Task Toggle()
        {
            if (busy) return;
            busy = true; connect.Enabled = false; install.Enabled = false;
            try
            {
                if (receiver == null)
                {
                    Receiver.PhoneUri(address.Text);
                    receiver = new Receiver(); receiver.Status = Report;
                    previousReceived = previousPresented = presentedFrames = 0;
                    previousErrors = previousLoss = 0; advice.Reset();
                    previousMetricTime = metricsClock.ElapsedMilliseconds;
                    lastPreviewFrame = null;
                    await receiver.Start(address.Text, true);
                    Directory.CreateDirectory(Path.GetDirectoryName(settings)); File.WriteAllText(settings, address.Text);
                    connect.Text = "Rozłącz"; address.Enabled = false;
                }
                else
                {
                    await audioPage.StopAudio();
                    await receiver.Stop(); receiver.Dispose(); receiver = null;
                    connect.Text = "Połącz"; address.Enabled = true;
                }
            }
            catch (Exception e)
            {
                if (receiver != null) { await receiver.Stop(); receiver.Dispose(); receiver = null; }
                status.Text = e.Message;
                connect.Text = "Połącz"; address.Enabled = true;
            }
            finally { busy = false; connect.Enabled = true; install.Enabled = receiver == null; if (exitRequested) Close(); }
        }
        async Task Install()
        {
            install.Enabled = false;
            try
            {
                var info = new ProcessStartInfo("powershell.exe", "-NoProfile -ExecutionPolicy Bypass -File \"" + Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Install-Camera.ps1") + "\"");
                info.UseShellExecute = true; info.Verb = "runas"; info.WindowStyle = ProcessWindowStyle.Hidden;
                using (var process = Process.Start(info))
                {
                    await Task.Run(delegate { process.WaitForExit(); });
                    status.Text = process.ExitCode == 0 ? "Kamera zainstalowana. Kliknij Połącz." : "Instalacja nie powiodła się. Sprawdź komunikat instalatora.";
                }
            }
            catch (Exception e) { status.Text = "Instalacja nie została ukończona: " + e.Message; }
            finally { install.Enabled = true; }
        }
        async Task RefreshPreview()
        {
            if (renderingPreview || closing || IsDisposed) return;
            var active = receiver;
            byte[] frame = active == null ? null : active.Latest;
            long now = metricsClock.ElapsedMilliseconds;
            if (now - previousMetricTime >= 1000)
            {
                long received = active == null ? 0 : active.Frames;
                double seconds = (now - previousMetricTime) / 1000.0;
                long errors = active == null ? 0 : Interlocked.Read(ref active.DecodeErrors);
                long loss = active == null ? 0 : Interlocked.Read(ref active.LostPackets);
                if (active != null) advice.Observe(active.DecoderRunning ? active.Input : null, active.Generation, seconds,
                    received - previousReceived, presentedFrames - previousPresented, errors - previousErrors, loss - previousLoss, PreviewActive);
                previousErrors = errors; previousLoss = loss;
                UpdateHardware(active);
                metrics.Text = active == null ? "" : String.Format("{0} · odbiór: {1:F0} kl./s · ", active.Codec, (received - previousReceived) / seconds) +
                    (PreviewActive ? String.Format("podgląd: {0:F0} kl./s", (presentedFrames - previousPresented) / seconds) : "podgląd wyłączony");
                if (!diagnosticMode) tray.Text = active == null ? "RemoteCam · rozłączono" : "RemoteCam · " + active.Codec + " · " + (active.DecoderRunning ? "transmisja aktywna" : "oczekiwanie na obraz");
                previousReceived = received; previousPresented = presentedFrames; previousMetricTime = now;
            }
            if (!PreviewActive) return;
            if (Object.ReferenceEquals(frame, lastPreviewFrame)) return;
            if (frame == null)
            {
                Image old = preview.Image; preview.Image = null; if (old != null) old.Dispose();
                lastPreviewFrame = null; return;
            }
            renderingPreview = true;
            previewConversions++;
            try
            {
                Bitmap next = await Task.Run(delegate { return Preview.Create(frame); });
                if (closing || IsDisposed || !PreviewActive || receiver != active || active.Latest == null) { next.Dispose(); return; }
                Image old = preview.Image; preview.Image = next; if (old != null) old.Dispose();
                lastPreviewFrame = frame; presentedFrames++;
            }
            finally { renderingPreview = false; }
        }
    }
}
