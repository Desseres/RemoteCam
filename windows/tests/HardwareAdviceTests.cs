using System;
using RemoteCamDesktop;

static class HardwareAdviceTests
{
    static void Check(bool condition, string name) { if (!condition) throw new Exception(name); }
    static void Main()
    {
        var input = InputVideo.Parse("Stream #0:0: Video: hevc (Main 10), yuv420p10le(tv, bt709), 4096x2304, 29.97 fps, 90k tbn");
        Check(input.Width == 4096 && input.Height == 2304 && input.Fps == 29.97 && input.Codec == "H.265", "parse actual input, not scaled output");
        Check(InputVideo.Parse("Stream #0:1: Audio: opus, 48000 Hz") == null, "ignore audio");
        var estimated = InputVideo.Parse("Stream #0:0: Video: hevc (Main), yuv420p(tv), 4096x2304, 30 tbr, 90k tbn");
        Check(estimated.Fps == 30 && estimated.EstimatedFps, "RTSP rate estimate must be labelled");
        var exact = InputVideo.Parse("Video: h264, yuv420p, 1920x1080, 15 fps, 30 tbr");
        Check(exact.Fps == 15 && !exact.EstimatedFps, "prefer declared FPS over estimate");
        var window = new StreamAdvice();
        for (int i = 0; i < 32; ++i) window.Observe(input, 1, 1, 30, 30, 0, 0);
        Check(window.Result == "", "wait for warmup plus full observation");
        window.Observe(input, 1, 1, 30, 30, 0, 0);
        Check(window.Result.Contains("utrzymał"), "healthy 30 fps");
        window.Observe(input, 1, 1, 30, 30, 0, 2);
        Check(window.Result.Contains("straty transmisji"), "new loss invalidates previous healthy result immediately");
        window.Observe(input, 2, 1, 30, 30, 0, 0);
        Check(window.Result == "" && window.Seconds == 0, "invalidate after reconnect");
        Check(StreamAdvice.Evaluate(input, 20, 20, 10, 0, 0).Contains("1920 × 1080"), "step down 4K");
        var fullHd = new InputVideo(1080, 1920, 30, "H.264");
        Check(StreamAdvice.Evaluate(fullHd, 20, 20, 10, 0, 0).Contains("1280 × 720"), "portrait input uses pixel count");
        Check(StreamAdvice.Evaluate(input, 30, 30, 0, 0, 3).Contains("Straty transmisji"), "loss takes precedence over FPS");
        Check(StreamAdvice.Evaluate(input, 20, 20, 10, 4, 0).Contains("błędy dekodowania"), "do not infer CPU limit from corruption");
        Check(StreamAdvice.Evaluate(input, 30, 30, 5, 0, 0).Contains("nie utrzymuje"), "bursts cannot hide stalls");
        Check(StreamAdvice.Evaluate(new InputVideo(1920, 1080, 15, "H.264"), 15, 15, 0, 0, 0).Contains("utrzymał"), "respect lower source FPS");
        Check(StreamAdvice.Evaluate(new InputVideo(1920, 1080, 60, "H.264"), 60, 30, 0, 0, 0).Contains("utrzymał"), "preview target is capped at 30");
        Check(StreamAdvice.Evaluate(new InputVideo(1920, 1080, 0, "H.264"), 30, 30, 0, 0, 0).Contains("brak wiarygodnej"), "unknown source rate must not pass");
        window.Observe(null, 2, 1, 0, 0, 0, 0);
        Check(window.Result == "" && window.Seconds == 0, "clear disconnected observation");
        Console.WriteLine("Hardware advice checks passed.");
        Console.WriteLine(HardwareProfile.Read().Description);
    }
}
