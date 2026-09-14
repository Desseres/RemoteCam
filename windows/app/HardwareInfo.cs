using System;
using System.Collections.Generic;
using System.Globalization;
using System.Management;
using System.Text.RegularExpressions;

namespace RemoteCamDesktop
{
    sealed class HardwareProfile
    {
        public string Description = "Odczytuję CPU, GPU i pamięć…";
        public static HardwareProfile Read()
        {
            var lines = new List<string>();
            ReadRows("SELECT Name, NumberOfCores, NumberOfLogicalProcessors FROM Win32_Processor", row =>
                lines.Add("CPU: " + row["Name"] + " · " + row["NumberOfCores"] + " rdzeni / " + row["NumberOfLogicalProcessors"] + " wątków"), lines, "CPU");
            ReadRows("SELECT Name, DriverVersion FROM Win32_VideoController", row =>
                lines.Add("GPU: " + row["Name"] + " · sterownik " + row["DriverVersion"]), lines, "GPU");
            ReadRows("SELECT TotalPhysicalMemory FROM Win32_ComputerSystem", row =>
                lines.Add("RAM: " + (Convert.ToDouble(row["TotalPhysicalMemory"]) / 1073741824).ToString("F0") + " GB"), lines, "RAM");
            return new HardwareProfile { Description = String.Join("\n", lines) };
        }
        static void ReadRows(string query, Action<ManagementBaseObject> accept, List<string> lines, string name)
        {
            try {
                var options = new EnumerationOptions { Timeout = TimeSpan.FromSeconds(3), ReturnImmediately = false };
                using (var search = new ManagementObjectSearcher(new ManagementScope("root\\cimv2"), new ObjectQuery(query), options))
                using (var rows = search.Get()) {
                    int count = 0;
                    foreach (ManagementBaseObject row in rows) { using (row) { accept(row); count++; } }
                    if (count == 0) lines.Add(name + ": brak danych w Windows");
                }
            } catch (Exception e) when (e is ManagementException || e is UnauthorizedAccessException || e is System.Runtime.InteropServices.COMException || e is System.Security.SecurityException) {
                lines.Add(name + ": nie udało się odczytać danych z Windows");
            }
        }
    }

    sealed class InputVideo
    {
        public readonly int Width, Height;
        public readonly double Fps;
        public readonly string Codec;
        public readonly bool EstimatedFps;
        public InputVideo(int width, int height, double fps, string codec, bool estimatedFps = false) { Width = width; Height = height; Fps = fps; Codec = codec; EstimatedFps = estimatedFps; }
        public string Description { get { return Width + " × " + Height + " · " + Codec + " · " + (Fps > 0 ? Fps.ToString("0.#") + " kl./s" + (EstimatedFps ? " (szacunek)" : "") : "nieznany FPS źródła"); } }
        public static InputVideo Parse(string line)
        {
            if (!line.Contains("Video: ")) return null;
            var size = Regex.Match(line, @"\b(\d{2,5})x(\d{2,5})\b");
            if (!size.Success) return null;
            var rate = Regex.Match(line, @"\b(\d+(?:\.\d+)?) fps\b");
            bool estimated = !rate.Success;
            if (estimated) rate = Regex.Match(line, @"\b(\d+(?:\.\d+)?) tbr\b");
            double fps = rate.Success ? Double.Parse(rate.Groups[1].Value, CultureInfo.InvariantCulture) : 0;
            if (fps > 240) fps = 0;
            string codec = line.Contains("hevc") ? "H.265" : line.Contains("h264") ? "H.264" : "inny kodek";
            return new InputVideo(Int32.Parse(size.Groups[1].Value), Int32.Parse(size.Groups[2].Value),
                fps, codec, estimated);
        }
    }

    // A rolling observation of the real pipeline, not a synthetic CPU ranking.
    sealed class StreamAdvice
    {
        public string Result = "";
        public double Seconds;
        double warmup, received, shown, slowSeconds;
        long errors, loss;
        object profile;
        int generation = -1;
        bool previewMeasured = true;
        public void Reset()
        {
            Result = ""; Seconds = warmup = received = shown = slowSeconds = 0;
            errors = loss = 0; profile = null; generation = -1;
        }
        public void Observe(InputVideo input, int attempt, double seconds, long frames, long previews, long decodeErrors, long lost, bool measurePreview = true)
        {
            if (input == null || seconds <= 0) { Reset(); return; }
            if (!Object.ReferenceEquals(profile, input) || generation != attempt || previewMeasured != measurePreview) { Reset(); profile = input; generation = attempt; previewMeasured = measurePreview; }
            if (warmup < 3) { warmup += seconds; return; }
            double target = input.Fps > 0 ? Math.Min(30, input.Fps) : 30;
            Seconds += seconds; received += Math.Max(0, frames); shown += Math.Max(0, previews);
            errors += Math.Max(0, decodeErrors); loss += Math.Max(0, lost);
            if (lost > 0) Result = "W bieżącym pomiarze wystąpiły straty transmisji. Zmniejsz bitrate i sprawdź Wi-Fi; poprzednia ocena płynności wymaga ponownej weryfikacji.";
            else if (decodeErrors > 0) Result = "W bieżącym pomiarze wystąpiły błędy dekodowania. Poprzednia ocena płynności wymaga ponownej weryfikacji.";
            if (frames / seconds < target * .9 || (measurePreview && previews / seconds < target * .9)) slowSeconds += seconds;
            if (Seconds < 30) return;
            Result = Evaluate(input, received / Seconds, shown / Seconds, slowSeconds, errors, loss, measurePreview);
            Seconds = received = shown = slowSeconds = 0; errors = loss = 0;
        }
        public static string Evaluate(InputVideo input, double receiveFps, double previewFps, double slowSeconds, long errors, long loss, bool measurePreview = true)
        {
            string summary = measurePreview ? String.Format("Pomiar 30 s: odbiór {0:F1}, podgląd {1:F1} kl./s.\n", receiveFps, previewFps) : String.Format("Pomiar 30 s: odbiór {0:F1} kl./s. Podgląd wyłączony.\n", receiveFps);
            if (input.EstimatedFps && input.Fps > 0) summary += "FPS źródła jest szacowany — porównaj go z ustawieniem telefonu.\n";
            if (loss > 0) return summary + "Straty transmisji: " + loss + " pakietów. Najpierw zmniejsz bitrate na telefonie i sprawdź połączenie Wi-Fi. Ten pomiar nie wyznacza limitu CPU/GPU.";
            if (errors > 0) return summary + "Wystąpiły błędy dekodowania. Spróbuj H.264 i niższego bitrate. Nie można potwierdzić stabilności tego profilu ani przypisać błędu wydajności CPU.";
            if (input.Fps <= 0) return summary + "Nie znamy FPS źródła — brak wiarygodnej oceny wydajności. Zacznij od H.264 / 1920 × 1080 / 30 kl./s i sprawdź ustawienia telefonu.";
            double target = Math.Min(30, input.Fps);
            if (receiveFps < target * .9 || (measurePreview && previewFps < target * .9) || slowSeconds > 3)
                return summary + "Ten profil nie utrzymuje płynności. Ustaw na telefonie " + LowerResolution(input) + " / maks. 30 kl./s i porównaj kolejny pomiar. Przy H.265 sprawdź też H.264. Przyczyną może być dekoder, obciążenie komputera lub telefon.";
            return summary + "Profil " + input.Description + " utrzymał zakładaną płynność w tym pomiarze. Możesz przy nim zostać. To sprawdzony profil, a nie fizyczne maksimum sprzętu; pomiar nie określa opóźnienia obrazu.";
        }
        static string LowerResolution(InputVideo input)
        {
            long pixels = (long)input.Width * input.Height;
            return pixels > 1920 * 1080 ? "1920 × 1080" : pixels > 1280 * 720 ? "1280 × 720" : "niższą dostępną rozdzielczość";
        }
    }
}
