using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace RemoteCamDesktop
{
    sealed class AudioPanel : Panel
    {
        readonly Func<Receiver> receiver;
        readonly ComboBox devices = new ComboBox();
        readonly Button enable = new Button(), install = new Button(), rename = new Button();
        readonly CheckBox mute = new CheckBox();
        readonly TrackBar volume = new TrackBar();
        readonly ProgressBar level = new ProgressBar();
        readonly Label state = new Label(), volumeText = new Label();
        readonly Timer timer = new Timer();
        AudioModule module;
        bool busy;
        public bool AudioActive { get { return module != null; } }
        public bool MicrophoneMuted { get { return mute.Checked; } set { mute.Checked = value; } }
        public AudioPanel(Func<Receiver> receiver)
        {
            this.receiver = receiver;
            BackColor = Color.FromArgb(28, 24, 21); ForeColor = Color.FromArgb(243, 242, 237); AutoScroll = true;
            var accent = Color.FromArgb(255, 225, 90);
            var content = new TableLayoutPanel { Dock = DockStyle.Top, AutoSize = true, ColumnCount = 1, Padding = new Padding(22, 12, 22, 20) };
            var title = new Label { Text = "REMOTECAM MICROPHONE", ForeColor = accent, Font = new Font("Segoe UI", 11, FontStyle.Bold), AutoSize = true };
            var description = new Label { Text = "Dźwięk z telefonu jako osobne wejście Windows. Włącz mikrofon w telefonie i kliknij Połącz w RemoteCam.", AutoSize = true };
            devices.DropDownStyle = ComboBoxStyle.DropDownList; devices.Width = 470; devices.BackColor = Color.FromArgb(40, 34, 29); devices.ForeColor = ForeColor;
            var actions = new FlowLayoutPanel { AutoSize = true, WrapContents = true };
            enable.Text = "Włącz mikrofon"; install.Text = "Zainstaluj VB-CABLE"; rename.Text = "Ustawienia audio Windows";
            foreach (var button in new[] { enable, install, rename }) {
                button.AutoSize = true; button.Height = 32; button.FlatStyle = FlatStyle.Flat;
                button.BackColor = Color.FromArgb(40, 34, 29); button.ForeColor = ForeColor;
                button.FlatAppearance.BorderColor = Color.FromArgb(86, 73, 51); actions.Controls.Add(button);
            }
            enable.BackColor = accent; enable.ForeColor = BackColor;
            mute.Text = "Wycisz mikrofon"; mute.AutoSize = true;
            volume.Minimum = 0; volume.Maximum = 100; volume.Value = 100; volume.TickFrequency = 10; volume.Width = 350;
            volumeText.Text = "Głośność do mikrofonu: 100%"; volumeText.AutoSize = true;
            level.Width = 470; level.Height = 12;
            state.Text = "Mikrofon wyłączony. Odsłuch lokalny wyłączony."; state.AutoSize = true;
            var guidance = new Label { AutoSize = true, ForeColor = Color.FromArgb(181, 171, 157), Text =
                "W OBS dodaj Przechwytywanie wejścia dźwięku → RemoteCam Microphone (VB-CABLE), albo CABLE Output. Ustaw Monitorowanie wyłączone. KH50 pozostaje osobnym źródłem.\n\nWycisz odtwarzacz telefonu w przeglądarce, aby uniknąć podwójnego audio. W Windows zostaw wyłączone „Nasłuchuj tego urządzenia”. Aplikacja nie odtwarza mikrofonu na głośnikach.\n\nVB-CABLE jest osobnym produktem VB-Audio (donationware). Jeśli jest przydatny, wesprzyj autora lub kup licencję. Nie jest usuwany razem z RemoteCam." };
            var link = new LinkLabel { Text = "VB-Audio — pobieranie i licencja", AutoSize = true, LinkColor = accent };
            link.LinkClicked += delegate { Process.Start(new ProcessStartInfo("https://vb-audio.com/Cable/") { UseShellExecute = true }); };
            foreach (Control item in new Control[] {title, description, devices, actions, mute, volumeText, volume, level, state, guidance, link}) {
                item.Margin = new Padding(0, 0, 0, 12); content.Controls.Add(item);
            }
            content.Resize += delegate {
                int width = Math.Max(200, content.ClientSize.Width - 48);
                foreach (Control item in content.Controls) item.MaximumSize = new Size(width, 0);
            };
            Controls.Add(content);
            mute.CheckedChanged += delegate { if (module != null) module.Buffer.Muted = mute.Checked; };
            volume.ValueChanged += delegate { volumeText.Text = "Głośność do mikrofonu: " + volume.Value + "%"; if (module != null) module.Buffer.Gain = volume.Value / 100f; };
            enable.Click += async delegate {
                if (busy) return;
                if (module != null) { await StopAudio(); return; }
                var selected = devices.SelectedItem as CableDevice;
                if (receiver() == null) { state.Text = "Najpierw kliknij Połącz, aby uruchomić odbiornik telefonu."; return; }
                if (selected == null) { state.Text = "Zainstaluj VB-CABLE i otwórz ponownie tę zakładkę."; return; }
                module = new AudioModule(); module.Buffer.Muted = mute.Checked; module.Buffer.Gain = volume.Value / 100f;
                module.Start(receiver(), selected.Id); devices.Enabled = install.Enabled = rename.Enabled = false;
                enable.Text = "Wyłącz mikrofon";
            };
            install.Click += async delegate { await InstallCable(); };
            rename.Click += delegate {
                try {
                    Process.Start(new ProcessStartInfo("ms-settings:sound") { UseShellExecute = true });
                    state.Text = "W Windows możesz zmienić nazwę wejścia CABLE Output na RemoteCam Microphone. Nie ustawiaj kabla jako domyślnych głośników.";
                } catch (Exception e) { state.Text = e.Message; }
            };
            timer.Interval = 100; timer.Tick += delegate {
                if (module == null) { level.Value = 0; return; }
                level.Value = Math.Max(0, Math.Min(100, (int)(module.Buffer.Peak * 100)));
                state.Text = (mute.Checked ? "WYCISZONY · " : "") + module.Status;
            }; timer.Start();
            VisibleChanged += delegate { timer.Interval = Visible ? 100 : 1000; };
            Disposed += delegate { timer.Dispose(); };
        }
        public void RefreshDevices()
        {
            if (module != null || busy) return;
            var selected = devices.SelectedItem as CableDevice;
            try {
                devices.Items.Clear();
                foreach (var device in CableDevice.List()) devices.Items.Add(device);
                if (devices.Items.Count > 0) devices.SelectedIndex = 0;
                install.Enabled = devices.Items.Count == 0;
                if (selected != null) foreach (CableDevice device in devices.Items) if (device.Id == selected.Id) devices.SelectedItem = device;
                if (devices.Items.Count == 0) state.Text = "Brak VB-CABLE. Kliknij Zainstaluj VB-CABLE. Po instalacji Windows może wymagać restartu.";
            } catch (Exception e) { state.Text = "Nie można odczytać urządzeń audio: " + e.Message; }
        }
        public async Task StopAudio()
        {
            if (module == null) return;
            busy = true; enable.Enabled = false;
            try { await module.Stop(); }
            finally { module = null; busy = false; enable.Text = "Włącz mikrofon"; enable.Enabled = devices.Enabled = install.Enabled = rename.Enabled = true; state.Text = "Mikrofon wyłączony. Odsłuch lokalny wyłączony."; }
        }
        async Task InstallCable()
        {
            if (busy) return;
            busy = true; enable.Enabled = install.Enabled = rename.Enabled = false;
            try {
                string zip = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "VBCABLE_Driver_Pack45.zip");
                using (var sha = SHA256.Create()) using (var file = File.OpenRead(zip))
                    if (BitConverter.ToString(sha.ComputeHash(file)).Replace("-", "") != "B950E39F01AF1D04EA623C8F6D8EB9B6EA5C477C637295FABF20631C85116BFB") throw new IOException("Nieprawidłowa suma kontrolna VB-CABLE.");
                string directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "RemoteCam", "VB-CABLE-" + Guid.NewGuid().ToString("N"));
                ZipFile.ExtractToDirectory(zip, directory);
                using (var process = Process.Start(new ProcessStartInfo(Path.Combine(directory, "VBCABLE_Setup_x64.exe")) { UseShellExecute = true, Verb = "runas" }))
                    await Task.Run(() => process.WaitForExit());
                state.Text = "Po instalacji sprawdź domyślne wyjście i mikrofon Windows — instalator VB-CABLE może je zmienić. Uruchom ponownie Windows, jeśli instalator tego wymaga. Wejście: CABLE Output.";
            } catch (Exception e) { state.Text = "Instalacja audio: " + e.Message; }
            finally { busy = false; enable.Enabled = install.Enabled = rename.Enabled = true; RefreshDevices(); }
        }
    }
}
