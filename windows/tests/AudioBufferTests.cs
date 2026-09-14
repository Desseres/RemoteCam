using System;
using System.Reflection;

static class AudioBufferTests
{
    static void Check(bool value, string message) { if (!value) throw new Exception(message); }
    static void Main(string[] args)
    {
        var assembly = Assembly.LoadFrom(args[0]);
        var type = assembly.GetType("RemoteCamDesktop.MicrophoneBuffer", true);
        var buffer = Activator.CreateInstance(type, true);
        byte[] source = new byte[3840], output = new byte[3840];
        for (int i = 0; i < source.Length; i += 4) Buffer.BlockCopy(BitConverter.GetBytes(.5f), 0, source, i, 4);
        Action add = () => type.GetMethod("Add").Invoke(buffer, new object[] {source, source.Length});
        Action read = () => type.GetMethod("Read").Invoke(buffer, new object[] {output, 0, output.Length});
        add(); type.GetField("Muted").SetValue(buffer, true); read();
        Check(Array.TrueForAll(output, b => b == 0), "mute must silence queued audio");
        type.GetField("Muted").SetValue(buffer, false); type.GetField("Gain").SetValue(buffer, .5f); add(); read();
        Check(BitConverter.ToSingle(output, 0) == .25f, "gain scales signal");
        read(); Check(Array.TrueForAll(output, b => b == 0), "underrun is silence, never repeats old samples");
        for (int i = 0; i < 40; i++) add();
        Check((int)type.GetProperty("BufferedBytes").GetValue(buffer) == 38400, "queue remains bounded to 100 ms");
        Check((long)type.GetField("DiscardedBytes").GetValue(buffer) > 0, "overflow drops buffered audio");
        type.GetMethod("Clear").Invoke(buffer, null); read();
        Check(Array.TrueForAll(output, b => b == 0), "disconnect clears audio");
        Console.WriteLine("PASS: mute queued audio, gain, silence on underrun, bounded overflow, disconnect clear.");
    }
}
