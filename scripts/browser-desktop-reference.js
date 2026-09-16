async (page) => {
  // Local development reference only: Next dev's eval loader conflicts with the
  // desktop production CSP. Keep the checked-out desktop files unchanged.
  await page.route('http://localhost:3007/', async route => {
    const response = await route.fetch();
    const headers = {...response.headers()};
    delete headers['content-security-policy'];
    await route.fulfill({response, headers});
  });
  await page.goto('http://localhost:3007/');
}
