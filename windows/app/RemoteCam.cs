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

namespace RemoteCamDesktop
{
    static class Program
    {
        [STAThread]
        static int Main(string[] args)
        {
            if (args.Length > 0 && args[0] == "--smoke")
                return Smoke(args).GetAwaiter().GetResult();
            if (args.Length > 0 && args[0] == "--self-test") return SelfTest();
            if (args.Length == 2 && args[0] == "--render-ui")
            {
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                using (var window = new MainWindow())
                {
                    window.ShowInTaskbar = false;
                    window.StartPosition = FormStartPosition.Manual;
                    window.Location = new Point(-32000, -32000);
                    window.Show();
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
                    log.AppendLine("frames=" + frames + " codec=" + receiver.Codec);
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
        long published;
        readonly Stopwatch clock = Stopwatch.StartNew();
        public void Publish(byte[] frame) { lock (gate) { latest = frame; published = clock.ElapsedMilliseconds; } }
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
                    byte[] frame = Latest;
                    if (frame != null && !Object.ReferenceEquals(frame, lastSent))
                    {
                        await pipe.WriteAsync(header, 0, header.Length, stop.Token);
                        await pipe.WriteAsync(frame, 0, frame.Length, stop.Token);
                        lastSent = frame;
                    }
                    await Task.Delay(33, stop.Token);
                }
            }
            catch (Exception) { }
            finally { lock (gate) pipes.Remove(pipe); pipe.Dispose(); }
        }
        public void Dispose()
        {
            stop.Cancel();
            lock (gate) { latest = null; foreach (var pipe in pipes) pipe.Dispose(); pipes.Clear(); }
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
        public string Codec = "—";
        string decoderError = "";
        public Action<string> Status;
        public long Frames { get { return Interlocked.Read(ref frames); } }
        public byte[] Latest { get { return broker == null ? null : broker.Latest; } }
        void Report(string value) { var handler = Status; if (handler != null) handler(value); }

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
            var process = new Process(); process.StartInfo = info;
            process.Start();
            try { job.Add(process); }
            catch { try { process.Kill(); } catch (Exception) { } process.Dispose(); throw; }
            lock (processesGate) processes.Add(process);
            return process;
        }
        void DrainErrors(Process p, bool codec)
        {
            p.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs e)
            {
                if (e.Data == null) return;
                if (codec && (e.Data.IndexOf("error", StringComparison.OrdinalIgnoreCase) >= 0 || e.Data.Contains("Option "))) decoderError = e.Data;
                if (codec && e.Data.Contains("Video: "))
                {
                    if (e.Data.Contains("hevc")) Codec = "H.265";
                    else if (e.Data.Contains("h264")) Codec = "H.264";
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
                        throw new IOException("Kamera Windows nie wystartowała. Użyj przycisku „Zainstaluj kamerę”. " + await camera.StandardError.ReadToEndAsync());
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
                try
                {
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
                    decoderError = "";
                    string args = "-hide_banner -loglevel info -nostdin -rtsp_transport tcp -timeout 5000000 -fflags nobuffer -flags low_delay -probesize 1000000 -analyzeduration 1000000 -i rtsp://127.0.0.1:" + rtsp + "/phone -an -sn -dn -vf \"scale=1920:1080:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1\" -pix_fmt nv12 -f rawvideo pipe:1";
                    decoder = Launch("ffmpeg.exe", args, false);
                    DrainErrors(decoder, true);
                    var output = decoder.StandardOutput.BaseStream;
                    bool firstFrame = true;
                    while (!token.IsCancellationRequested)
                    {
                        byte[] frame = new byte[FrameBroker.FrameSize];
                        int offset = 0;
                        while (offset < frame.Length)
                        {
                            Task<int> read = output.ReadAsync(frame, offset, frame.Length - offset, token);
                            if (await Task.WhenAny(read, Task.Delay(8000, token)) != read)
                                throw new IOException("Telefon przestał wysyłać obraz.");
                            int count = await read;
                            if (count == 0) throw new IOException("Połączenie wideo zostało przerwane. " + decoderError);
                            offset += count;
                        }
                        broker.Publish(frame);
                        Interlocked.Increment(ref frames);
                        if (firstFrame) { Report("Połączono · " + Codec + " · wyjście 1920 × 1080 / 30 fps"); firstFrame = false; }
                    }
                }
                catch (Exception e) { if (!token.IsCancellationRequested) Report(e.Message + " Ponawiam za 2 s…"); }
                finally { broker.Clear(); EndProcess(decoder); EndProcess(relay); }
                if (!token.IsCancellationRequested) { try { await Task.Delay(2000, token); } catch (OperationCanceledException) { } }
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
        readonly TextBox address = new TextBox();
        readonly Button connect = new Button(), install = new Button();
        readonly Label status = new Label(), metrics = new Label();
        readonly PictureBox preview = new PictureBox();
        readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();
        readonly string settings = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "RemoteCam", "desktop-address.txt");
        Receiver receiver;
        bool busy, closing;
        public MainWindow()
        {
            Text = "RemoteCam Desktop · wersja testowa 0.1.0";
            ClientSize = new Size(900, 700); MinimumSize = new Size(740, 620);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(17, 23, 34); ForeColor = Color.White;
            Font = new Font("Segoe UI", 10);
            var title = new Label { Text = "Telefon jako kamera Windows", Font = new Font("Segoe UI", 22, FontStyle.Bold), Dock = DockStyle.Top, Height = 58, Padding = new Padding(20, 12, 0, 0) };
            var help = new Label { Text = "Włącz Stream w RemoteCam na telefonie i wybierz H.264 lub H.265 + WebRTC.", Dock = DockStyle.Top, Height = 40, Padding = new Padding(22, 5, 0, 0) };
            var connection = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 54, Padding = new Padding(22, 5, 0, 0) };
            address.Width = 330; address.Text = File.Exists(settings) ? File.ReadAllText(settings) : "192.168.1.11:8080";
            connect.Text = "Połącz"; connect.Width = 130; connect.Height = 32;
            install.Text = "Zainstaluj kamerę"; install.Width = 170; install.Height = 32;
            foreach (Button b in new[] {connect, install}) { b.FlatStyle = FlatStyle.Flat; b.BackColor = Color.FromArgb(37, 90, 165); }
            connection.Controls.AddRange(new Control[] {address, connect, install});
            preview.Dock = DockStyle.Fill; preview.BackColor = Color.FromArgb(8, 12, 20); preview.SizeMode = PictureBoxSizeMode.Zoom;
            var footer = new Panel { Dock = DockStyle.Bottom, Height = 170, Padding = new Padding(20) };
            string registered = Microsoft.Win32.Registry.GetValue(@"HKEY_LOCAL_MACHINE\SOFTWARE\Classes\CLSID\{D168A389-283B-4AD8-ACB4-1CC943368FA0}\InprocServer32", "", null) as string;
            status.Text = registered != null && File.Exists(registered) ? "Kamera zainstalowana. Włącz Stream na telefonie i kliknij Połącz." : "Gotowy. Przy pierwszym uruchomieniu zainstaluj kamerę."; status.Dock = DockStyle.Top; status.Height = 45;
            metrics.Dock = DockStyle.Top; metrics.Height = 25;
            var instructions = new Label { Text = "W OBS: Urządzenie do przechwytywania wideo → RemoteCam (wirtualna kamera Windows).\nTa wersja udostępnia obraz. Mikrofon wybierz osobno w programie odbiorczym.", Dock = DockStyle.Bottom, Height = 52, ForeColor = Color.Silver };
            footer.Controls.Add(instructions); footer.Controls.Add(metrics); footer.Controls.Add(status);
            Controls.Add(preview); Controls.Add(footer); Controls.Add(connection); Controls.Add(help); Controls.Add(title);
            connect.Click += async delegate { await Toggle(); };
            install.Click += async delegate { await Install(); };
            timer.Interval = 250; timer.Tick += delegate { RefreshPreview(); }; timer.Start();
            FormClosing += async delegate(object sender, FormClosingEventArgs e)
            {
                if (closing) return;
                e.Cancel = true;
                if (busy) return;
                busy = true; connect.Enabled = false;
                if (receiver != null) { await receiver.Stop(); receiver.Dispose(); receiver = null; }
                closing = true; timer.Stop(); Close();
            };
        }
        void Report(string value)
        {
            if (!IsDisposed && IsHandleCreated) BeginInvoke((Action)delegate { status.Text = value; });
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
                    await receiver.Start(address.Text, true);
                    Directory.CreateDirectory(Path.GetDirectoryName(settings)); File.WriteAllText(settings, address.Text);
                    connect.Text = "Rozłącz"; address.Enabled = false;
                }
                else
                {
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
            finally { busy = false; connect.Enabled = true; install.Enabled = receiver == null; }
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
        void RefreshPreview()
        {
            byte[] frame = receiver == null ? null : receiver.Latest;
            Image next = frame == null ? null : Preview.Create(frame);
            Image old = preview.Image; preview.Image = next; if (old != null) old.Dispose();
            metrics.Text = receiver == null ? "" : receiver.Codec + " · odebrane klatki: " + receiver.Frames;
        }
    }
}
