<?php
declare(strict_types=1);
$config = require __DIR__ . '/config.php';
$requested = $_GET['lang'] ?? null;
$language = is_string($requested) && in_array($requested, ['pl', 'en'], true) ? $requested : 'pl';
$translations = require __DIR__ . '/translations.php';
function e(string $value): string { return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }
function t(string $key): string {
    global $translations, $language;
    return $translations[$language][$key] ?? $key;
}
function asset(string $path): string {
    return $path . '?v=' . substr(hash_file('sha256', __DIR__ . '/' . $path), 0, 10);
}
function platformIcon(string $platform): string {
    $shape = $platform === 'android'
        ? '<path d="M7 7h10a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2v3h-2v-3H9v3H7v-3a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2ZM3 8H1v8h2Zm20 0h-2v8h2ZM6 6a6 6 0 0 1 1.6-3L6.4 1.4 7.6.6l1.2 1.6a6 6 0 0 1 6.4 0L16.4.6l1.2.8L16.4 3A6 6 0 0 1 18 6Z"/><circle cx="9" cy="4.5" r=".6" fill="var(--icon-eye, var(--bg))"/><circle cx="15" cy="4.5" r=".6" fill="var(--icon-eye, var(--bg))"/>'
        : '<path d="M2 3h9v9H2zm11 0h9v9h-9zM2 14h9v9H2zm11 0h9v9h-9z"/>';
    return '<svg class="platform-icon" viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" focusable="false" fill="currentColor">' . $shape . '</svg>';
}
header('Content-Language: ' . $language);
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: strict-origin-when-cross-origin');
header("Content-Security-Policy: default-src 'self'; img-src 'self'; script-src 'self'; style-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
