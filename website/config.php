<?php
declare(strict_types=1);
// Public configuration only. Deployment credentials belong in ../.env.deploy.
return [
    'site_url' => 'https://remotecam.kasztelan.me',
    'repository' => 'https://github.com/Desseres/RemoteCam',
    'windows_version' => '0.1.10',
    'windows_download' => 'https://github.com/Desseres/RemoteCam/releases/download/windows-v0.1.10/RemoteCam-Desktop-0.1.10-test-Setup.exe',
    'windows_release' => 'https://github.com/Desseres/RemoteCam/releases/tag/windows-v0.1.10',
    'contact' => 'apps@kasztelan.me',
    // Enable only after the listing is publicly available.
    'play_store_url' => '',
];
