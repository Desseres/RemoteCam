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
header('Content-Language: ' . $language);
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: strict-origin-when-cross-origin');
header("Content-Security-Policy: default-src 'self'; img-src 'self'; script-src 'self'; style-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
