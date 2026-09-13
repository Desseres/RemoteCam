const lightbox = document.querySelector('#lightbox');
let opener;
document.querySelectorAll('.screenshot').forEach(link => link.addEventListener('click', event => {
  if (!lightbox?.showModal) return;
  event.preventDefault(); opener = link;
  lightbox.querySelector('img').src = link.href;
  lightbox.querySelector('img').alt = link.dataset.title;
  lightbox.querySelector('p').textContent = link.dataset.title;
  lightbox.showModal();
}));
lightbox?.querySelector('button').addEventListener('click', () => lightbox.close());
lightbox?.addEventListener('click', event => { if (event.target === lightbox) lightbox.close(); });
lightbox?.addEventListener('close', () => opener?.focus());
document.querySelectorAll('[data-copy]').forEach(button => button.addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(button.dataset.copy);
    document.querySelector('#copy-status').textContent = `${button.dataset.done}: ${button.dataset.copy}`;
  } catch { document.querySelector('#copy-status').textContent = button.dataset.error; }
}));
