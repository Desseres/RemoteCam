using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace RemoteCamDesktop
{
    sealed class AboutPanel : Panel
    {
        public AboutPanel()
        {
            AutoScroll = true;
            var accent = Color.FromArgb(255, 225, 90);
            var muted = Color.FromArgb(181, 171, 157);
            var cards = new TableLayoutPanel { Dock = DockStyle.Top, AutoSize = true, ColumnCount = 1, Padding = new Padding(22, 16, 22, 18) };
            var title = new Label { Text = "RemoteCam Desktop", Font = new Font("Segoe UI", 19, FontStyle.Bold) };
            var version = new Label { Text = "WERSJA " + Brand.Version + " TEST  ·  WINDOWS 11", ForeColor = accent };
            var description = new Label { Text = "Zamień telefon w kamerę do OBS i innych aplikacji Windows.\nObraz przez sieć lokalną, opcjonalny mikrofon i działanie w trayu." };
            var actions = new FlowLayoutPanel { AutoSize = true, Dock = DockStyle.Top, Margin = new Padding(0, 2, 0, 12) };
            AddButton(actions, "Strona RemoteCam ↗", "https://remotecam.kasztelan.me/?lang=pl", false);
            AddButton(actions, "Pobierz APK na telefon ↗", "https://remotecam.kasztelan.me/?lang=pl#download", true);
            AddButton(actions, "Repozytorium GitHub ↗", "https://github.com/Desseres/RemoteCam", false);
            var download = new Label { Text = "APK pobierzesz ze strony projektu. Przyciski otwierają domyślną przeglądarkę.", ForeColor = muted };
            var author = new Label { Text = "Rozwój RemoteCam: Paweł Kasztelan (Desseres)\n© 2026 — modernizacja Androida, streaming i aplikacja Windows." };
            var original = new Label { Text = "Projekt bazuje na RemoteCam autorstwa Thomasa SIMONA (Ruddle), © 2023.\nKod RemoteCam: licencja MIT. Biblioteki i VB-CABLE mają osobne licencje.", ForeColor = muted };
            var links = new FlowLayoutPanel { AutoSize = true, Dock = DockStyle.Top, Margin = Padding.Empty };
            AddLink(links, "Zgłoś problem", "https://github.com/Desseres/RemoteCam/issues");
            AddLink(links, "Licencja RemoteCam", Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "LICENSE.txt"));
            AddLink(links, "Licencje składników", Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "licenses"));
            foreach (Control item in new Control[] { title, version, description, actions, download, author, original, links }) {
                var label = item as Label;
                if (label != null) { label.AutoSize = true; label.Margin = new Padding(0, 0, 0, 12); }
                cards.Controls.Add(item);
            }
            cards.Resize += delegate {
                int width = Math.Max(200, cards.ClientSize.Width - cards.Padding.Horizontal);
                foreach (Control item in cards.Controls) item.MaximumSize = new Size(width, 0);
            };
            Controls.Add(cards);
        }
        void AddButton(FlowLayoutPanel parent, string text, string target, bool primary)
        {
            var button = new Button { Text = text, AutoSize = true, Height = 38, Padding = new Padding(10, 4, 10, 4), FlatStyle = FlatStyle.Flat, Cursor = Cursors.Hand, Margin = new Padding(0, 0, 10, 6), BackColor = primary ? Color.FromArgb(255, 225, 90) : Color.FromArgb(40, 34, 29), ForeColor = primary ? Color.FromArgb(28, 24, 21) : Color.FromArgb(243, 242, 237) };
            button.FlatAppearance.BorderColor = Color.FromArgb(86, 73, 51);
            button.Click += delegate { Open(target); };
            parent.Controls.Add(button);
        }
        void AddLink(FlowLayoutPanel parent, string text, string target)
        {
            var link = new LinkLabel { Text = text, AutoSize = true, LinkColor = Color.FromArgb(255, 225, 90), ActiveLinkColor = Color.White, VisitedLinkColor = Color.FromArgb(255, 225, 90), Margin = new Padding(0, 0, 20, 6) };
            link.LinkClicked += delegate { Open(target); };
            parent.Controls.Add(link);
        }
        void Open(string target)
        {
            try { Process.Start(new ProcessStartInfo(target) { UseShellExecute = true }); }
            catch (Exception e) { MessageBox.Show(this, "Nie można otworzyć:\n" + target + "\n\n" + e.Message, "RemoteCam — informacje", MessageBoxButtons.OK, MessageBoxIcon.Information); }
        }
    }
}
