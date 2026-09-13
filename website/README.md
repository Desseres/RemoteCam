# RemoteCam website

PHP 8.1+ / plain CSS / vanilla JavaScript. No Node build step is needed in production.
The website follows the Image Viewer / TraceLens project structure, adapted for RemoteCam.

See [the deployment guide](../docs/website.md) for local preview and publishing.
Public configuration: `config.php`. Never put credentials in this directory.

Validated locally with PHP 8.3 and Chrome at 1440 × 1100 and 390 × 844:

- PHP syntax; both languages and invalid language input fallback;
- gallery opening, Escape closing and return focus;
- all four screenshots loaded without distortion;
- clipboard example, signed APK download and matching SHA-256;
- privacy text and publisher; no browser errors or failed asset requests;
- no horizontal document overflow on mobile;
- preparation script in local-only mode; `.env.deploy` ignored by Git.

Actual FTPS upload was not executed. Hosting credentials, directory permissions, domain
routing and TLS configuration still need to be supplied and verified for deployment.
