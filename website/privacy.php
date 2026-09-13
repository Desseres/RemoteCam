<?php
require __DIR__ . '/bootstrap.php';
$policy = file_get_contents(__DIR__ . '/privacy-policy.txt');
$sections = preg_split('/\R\s*\R/', trim($policy));
?>
<!doctype html><html lang="<?= e($language) ?>"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><?= e(t('privacy')) ?> — RemoteCam</title><link rel="icon" href="assets/icon.png">
<link rel="stylesheet" href="<?= e(asset('styles.css')) ?>">
<link rel="canonical" href="<?= e($config['site_url']) ?>/privacy.php?lang=<?= e($language) ?>">
</head><body><header class="nav wrap"><a class="brand" href="index.php?lang=<?= e($language) ?>"><img src="assets/icon.png" width="40" height="40" alt="">RemoteCam</a><a class="text-link" href="index.php?lang=<?= e($language) ?>">← <?= e(t('back')) ?></a><div class="languages"><a href="?lang=pl" lang="pl">PL</a><a href="?lang=en" lang="en">EN</a></div></header>
<main class="policy"><p class="eyebrow">DESSERES · REMOTECAM</p><h1><?= e(t('privacy')) ?></h1><p><?= e(t('privacyIntro')) ?></p>
<article lang="en"><?php foreach ($sections as $i=>$section): $lines=preg_split('/\R/', $section, 2); ?><section><h2><?= e($lines[0]) ?></h2><p><?= nl2br(e($lines[1] ?? '')) ?></p></section><?php endforeach; ?></article>
<aside class="network"><p><?= e(t('sitePrivacy')) ?></p></aside><a class="text-link" href="mailto:<?= e($config['contact']) ?>"><?= e($config['contact']) ?></a></main></body></html>
