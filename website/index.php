<?php
require __DIR__ . '/bootstrap.php';
$versions = json_decode(file_get_contents(__DIR__ . '/versions.json'), true, 512, JSON_THROW_ON_ERROR);
$current = $versions[0];
$apkAvailable = isset($current['apk']) && is_file(__DIR__ . '/' . $current['apk']);
$downloadUrl = $apkAvailable ? $current['apk'] : $config['repository'];
$downloadLabel = $apkAvailable ? t('download') : t('source');
?>
<!doctype html>
<html lang="<?= e($language) ?>">
<head>
 <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
 <title>RemoteCam — <?= e(t('hero1') . ' ' . t('hero2') . ' ' . t('hero3')) ?></title>
 <meta name="description" content="<?= e(t('description')) ?>"><meta name="theme-color" content="#151515">
 <meta property="og:title" content="RemoteCam"><meta property="og:description" content="<?= e(t('description')) ?>">
 <meta property="og:type" content="website"><meta property="og:url" content="<?= e($config['site_url']) ?>/">
 <meta property="og:image" content="<?= e($config['site_url']) ?>/assets/og.png">
 <link rel="canonical" href="<?= e($config['site_url']) ?>/?lang=<?= e($language) ?>">
 <link rel="alternate" hreflang="pl" href="<?= e($config['site_url']) ?>/?lang=pl">
 <link rel="alternate" hreflang="en" href="<?= e($config['site_url']) ?>/?lang=en">
 <link rel="alternate" hreflang="x-default" href="<?= e($config['site_url']) ?>/">
 <link rel="icon" href="assets/icon.png"><link rel="stylesheet" href="<?= e(asset('styles.css')) ?>">
 <script src="<?= e(asset('app.js')) ?>" defer></script>
</head>
<body>
<a class="skip" href="#main"><?= e(t('skip')) ?></a>
<header class="nav wrap">
 <a class="brand" href="?lang=<?= e($language) ?>"><img src="assets/icon.png" width="40" height="40" alt="">RemoteCam</a>
 <nav aria-label="<?= e(t('nav')) ?>">
  <?php foreach (['features','how','gallery','versions'] as $id): ?><a href="#<?= e($id) ?>"><?= e(t($id)) ?></a><?php endforeach; ?>
 </nav>
 <div class="languages" aria-label="Language">
  <?php foreach (['pl','en'] as $lang): ?><a href="?lang=<?= $lang ?>" lang="<?= $lang ?>"<?= $language === $lang ? ' aria-current="page"' : '' ?>><?= strtoupper($lang) ?></a><?php endforeach; ?>
 </div>
 <a class="button small" href="#download"><?= e(t('download')) ?> <span aria-hidden="true">↗</span></a>
</header>
<main id="main">
 <section class="hero wrap" id="start">
  <div class="hero-copy">
   <p class="eyebrow"><span class="status-dot"></span><?= e(t('eyebrow')) ?></p>
   <h1><?= e(t('hero1')) ?><br><em><?= e(t('hero2')) ?><br><?= e(t('hero3')) ?></em></h1>
   <p class="lead"><?= e(t('lead')) ?></p>
   <div class="actions"><a class="button accent" href="<?= e($downloadUrl) ?>"<?= $apkAvailable ? ' download' : '' ?>><?= e($downloadLabel) ?> <span aria-hidden="true">↓</span></a><a class="text-link" href="#how"><?= e(t('see')) ?> →</a></div>
   <ul class="facts"><li>Android 9+</li><li><?= e(t('free')) ?></li><li>H.264 · Opus · JPEG</li></ul>
  </div>
  <figure class="hero-visual"><img src="assets/hero.png" width="1254" height="1254" alt="<?= e(t('heroAlt')) ?>" fetchpriority="high"><figcaption><span>PHONE</span><span class="signal-line" aria-hidden="true"></span><span>COMPUTER</span></figcaption></figure>
 </section>
 <section class="trust wrap" aria-label="RemoteCam">
  <?php foreach (['noAccount','noCloud','noAds'] as $key): ?><div><strong><?= e(t($key)) ?></strong><p><?= e(t($key.'Text')) ?></p></div><?php endforeach; ?>
 </section>
 <section class="section wrap" id="features">
  <div class="section-heading"><p class="eyebrow"><?= e(t('featuresEyebrow')) ?></p><h2><?= t('featuresTitle') ?></h2><p><?= e(t('featuresIntro')) ?></p></div>
  <div class="feature-grid"><?php for ($n=1;$n<=6;$n++): ?><article class="feature"><span class="number">0<?= $n ?></span><h3><?= e(t('f'.$n)) ?></h3><p><?= e(t('f'.$n.'body')) ?></p></article><?php endfor; ?></div>
 </section>
 <section class="workflow" id="how"><div class="wrap">
  <p class="eyebrow"><?= e(t('howEyebrow')) ?></p><h2><?= e(t('howTitle')) ?></h2>
  <ol class="steps"><?php for ($n=1;$n<=3;$n++): ?><li><span>0<?= $n ?></span><h3><?= e(t('step'.$n)) ?></h3><p><?= e(t('step'.$n.'body')) ?></p></li><?php endfor; ?></ol>
 </div></section>
 <section class="section formats wrap">
  <div class="section-heading compact"><h2><?= e(t('formatsTitle')) ?></h2><p><?= e(t('formatsIntro')) ?></p></div>
  <div class="table-scroll"><table><thead><tr><th><?= e(t('format')) ?></th><th><?= e(t('receiver')) ?></th><th><?= e(t('address')) ?></th></tr></thead><tbody>
  <?php foreach ([['H.264 + WebRTC',t('browser'),'http://PHONE_IP:8080/webrtc'],['H.264 + WHEP','go2rtc','webrtc:http://PHONE_IP:8080/whep'],['JPEG Browser',t('browser'),'http://PHONE_IP:8080/view'],['MJPEG',t('mjpeg'),'http://PHONE_IP:8080/cam.mjpeg']] as [$format,$receiver,$url]): ?>
   <tr><th scope="row"><?= e($format) ?></th><td><?= e($receiver) ?></td><td><code><?= e($url) ?></code><button class="copy" data-copy="<?= e($url) ?>" data-done="<?= e(t('copied')) ?>" data-error="<?= e(t('copyFailed')) ?>" aria-label="<?= e(t('copy').' '.$format) ?>"><?= e(t('copy')) ?></button></td></tr>
  <?php endforeach; ?></tbody></table></div>
  <p class="audio-note"><?= e(t('audio')) ?></p>
  <aside class="audio-guide"><h3><?= e(t('audioTitle')) ?></h3><p><?= e(t('audioSetup')) ?></p><p><?= e(t('audioMute')) ?></p></aside>
  <div class="actions"><a class="text-link" href="<?= e($config['repository']) ?>/blob/main/docs/go2rtc.md"><?= e(t('go2rtcLink')) ?> ↗</a><a class="text-link" href="https://github.com/AlexxIT/go2rtc/releases/"><?= e(t('go2rtcDownload')) ?> ↗</a></div><p id="copy-status" role="status"></p>
 </section>
 <section class="section wrap" id="gallery">
  <div class="section-heading"><p class="eyebrow"><?= e(t('galleryEyebrow')) ?></p><h2><?= e(t('galleryTitle')) ?></h2><p><?= e(t('galleryIntro')) ?></p></div>
  <div class="gallery"><?php foreach (['camera','focus','receivers','guide'] as $image): ?><figure><a class="screenshot" href="assets/<?= $image ?>.png" data-title="<?= e(t($image)) ?>"><img src="assets/<?= $image ?>.png" alt="<?= e(t($image)) ?>" width="1280" height="2800" loading="lazy"></a><figcaption><?= e(t($image)) ?></figcaption></figure><?php endforeach; ?></div>
 </section>
 <section class="section wrap versions" id="versions"><div class="section-heading"><p class="eyebrow"><?= e(t('versionsEyebrow')) ?></p><h2><?= e(t('versionsTitle')) ?></h2></div><div>
  <?php foreach ($versions as $i=>$version): ?><article class="version"><div><strong><?= e($version['version']) ?></strong><time datetime="<?= e($version['date']) ?>"><?= e($version['date']) ?></time><?php if ($i===0): ?><span class="tag"><?= e(t('latest')) ?></span><?php endif; ?></div><div><h3><?= e($version['title'][$language]) ?></h3><p><?= e($version['description'][$language]) ?></p></div></article><?php endforeach; ?>
 </div></section>
 <section class="download wrap" id="download"><div><p class="eyebrow">REMOTECAM <?= e($current['version']) ?></p><h2><?= t('downloadTitle') ?></h2><p><?= e(t('downloadBody')) ?></p></div><div class="download-actions">
  <a class="button accent" href="<?= e($downloadUrl) ?>"<?= $apkAvailable ? ' download' : '' ?>><?= e($downloadLabel) ?> ↓</a>
  <?php if ($apkAvailable): ?><a class="text-link" href="<?= e($current['apk']) ?>.sha256" download><?= e(t('checksum')) ?> ↗</a><?php endif; ?>
  <?php if ($config['play_store_url']): ?><a class="button" href="<?= e($config['play_store_url']) ?>"><?= e(t('play')) ?></a><?php else: ?><p class="muted"><?= e(t('playPending')) ?></p><?php endif; ?>
 </div><p class="migration"><?= e(t('migration')) ?></p></section>
 <aside class="network wrap"><h3><?= e(t('networkTitle')) ?></h3><p><?= e(t('networkBody')) ?></p></aside>
 <section class="thanks wrap"><h3><?= e(t('thanks')) ?></h3><p><?= e(t('thanksBody')) ?></p><a class="text-link" href="https://github.com/Ruddle/RemoteCam"><?= e(t('original')) ?> ↗</a></section>
</main>
<footer class="wrap"><div><a class="brand" href="#start">RemoteCam</a><p>© <?= date('Y') ?> Desseres · MIT</p></div><div><a href="privacy.php?lang=<?= e($language) ?>"><?= e(t('privacy')) ?></a><a href="<?= e($config['repository']) ?>">GitHub</a><a href="mailto:<?= e($config['contact']) ?>"><?= e(t('contact')) ?></a><p><?= e(t('copyright')) ?></p></div></footer>
<dialog id="lightbox"><button class="lightbox-close" aria-label="<?= e(t('close')) ?>">×</button><p></p><img alt=""></dialog>
</body></html>
