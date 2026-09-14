using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

// Process-level tests: a private HTTP fixture stands in for go2rtc and an idle
// child stands in for a decoder blocked without producing complete frames.
class ReceiverWatchdogTests
{
    static int Main(string[] args)
    {
        if (args[0] == "--idle") { Thread.Sleep(30000); return 0; }
        if (args[0] == "--gpu-error") { Console.Error.WriteLine("Device setup failed for decoder: Generic error in an external library"); return 1; }
        try { Run(args[0]).GetAwaiter().GetResult(); Console.WriteLine("PASS: stalled frames, source replacement, transient empty snapshot, cancellation"); return 0; }
        catch (Exception e) { Console.Error.WriteLine(e); return 1; }
    }

    static async Task Run(string assembly)
    {
        var type = Assembly.LoadFrom(assembly).GetType("RemoteCamDesktop.Receiver", true);
        await Check(type, true, false);
        await Check(type, false, true);
        await Check(type, false, false);
        object receiver = Activator.CreateInstance(type, true);
        using (var failed = Process.Start(new ProcessStartInfo(Assembly.GetExecutingAssembly().Location, "--gpu-error") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardError = true })) {
            type.GetMethod("DrainErrors", BindingFlags.Instance | BindingFlags.NonPublic).Invoke(receiver, new object[] { failed, true });
            failed.WaitForExit();
            if (!(bool)type.GetField("hardwareFailed", BindingFlags.Instance | BindingFlags.NonPublic).GetValue(receiver))
                throw new Exception("GPU initialization failure was not detected for software fallback");
        }
        ((IDisposable)receiver).Dispose();
    }

    static async Task Check(Type type, bool stalled, bool replace)
    {
        object receiver = Activator.CreateInstance(type, true);
        var flags = BindingFlags.Instance | BindingFlags.NonPublic;
        var ticks = type.GetField("lastFrameTicks", flags);
        ticks.SetValue(receiver, stalled ? Stopwatch.GetTimestamp() - 5 * Stopwatch.Frequency : Stopwatch.GetTimestamp());
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        int port = ((IPEndPoint)listener.LocalEndpoint).Port;
        using (var stop = new CancellationTokenSource())
        using (var child = Process.Start(new ProcessStartInfo(Assembly.GetExecutingAssembly().Location, "--idle") { UseShellExecute = false, CreateNoWindow = true }))
        {
            int requests = 0;
            var server = Task.Run(async delegate {
                try {
                    while (!stop.IsCancellationRequested) {
                        using (var client = await listener.AcceptTcpClientAsync())
                        using (var stream = client.GetStream())
                        using (var reader = new StreamReader(stream, Encoding.ASCII, false, 1024, true)) {
                            string line;
                            do { line = await reader.ReadLineAsync(); } while (!String.IsNullOrEmpty(line));
                            int request = ++requests;
                            string producers = request == 2 ? "[]" : "[{\"id\":" + (replace && request >= 3 ? 2 : 1) + "}]";
                            string body = "{\"phone\":{\"producers\":" + producers + "}}";
                            byte[] response = Encoding.ASCII.GetBytes("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: " + body.Length + "\r\n\r\n" + body);
                            await stream.WriteAsync(response, 0, response.Length);
                        }
                    }
                } catch (ObjectDisposedException) { } catch (SocketException) { }
            });
            Task monitor = (Task)type.GetMethod("MonitorPipeline", flags).Invoke(receiver, new object[] { port, child, stop.Token });
            try {
                if (stalled || replace) {
                    if (await Task.WhenAny(monitor, Task.Delay(3500)) != monitor) throw new Exception("Monitor failed to stop decoder: stalled=" + stalled);
                    await monitor;
                    if (!child.WaitForExit(1000)) throw new Exception("Decoder stayed alive after recovery request");
                } else {
                    await Task.Delay(2200);
                    if (monitor.IsCompleted || child.HasExited) throw new Exception("Healthy source or transient empty snapshot triggered restart");
                }
            } finally {
                stop.Cancel(); listener.Stop();
                try { await monitor; } catch (OperationCanceledException) { }
                await server;
                if (!child.HasExited) { child.Kill(); child.WaitForExit(1000); }
                ((IDisposable)receiver).Dispose();
            }
        }
    }
}
