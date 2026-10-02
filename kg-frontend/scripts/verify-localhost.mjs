// Runtime smoke check: real Next assets, no intercepted APIs or auth bypass.
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const loadPackage = createRequire(import.meta.url);
const { chromium } = loadPackage('playwright');
const AxeBuilder = loadPackage('@axe-core/playwright').default;
const output = path.resolve(fileURLToPath(new URL('.', import.meta.url)), '../.impeccable/review');
fs.mkdirSync(output, { recursive: true });
const report = { hosts: [], views: [], pageErrors: [], failedAssets: [] };
const browser = await chromium.launch({ headless: true, ...(process.env.KG_CHROME_PATH ? { executablePath: process.env.KG_CHROME_PATH } : process.platform === 'darwin' ? { executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' } : {}) });
try {
  for (const hostname of ['localhost', '127.0.0.1', '[::1]']) {
    const context = await browser.newContext({ viewport: { width: 1280, height: 720 }, reducedMotion: 'reduce' });
    const page = await context.newPage();
    page.on('pageerror', error => report.pageErrors.push(error.message));
    page.on('requestfailed', request => { if (request.url().includes('/_next/')) report.failedAssets.push(request.url()); });
    page.on('response', response => { if (response.url().includes('/_next/') && response.status() >= 400) report.failedAssets.push(response.url()); });
    const response = await page.goto(`http://${hostname}:3100/login`);
    assert.equal(response.status(), 200);
    await page.getByRole('heading', { name: 'Đăng nhập', exact: true }).waitFor();
    await page.evaluate(() => document.fonts.ready);
    assert.equal(await page.evaluate(() => getComputedStyle(document.body).backgroundColor), 'rgb(243, 238, 228)');
    report.hosts.push({ hostname, status: response.status(), title: await page.title() });
    if (hostname === 'localhost') {
      for (const [device, width, height] of [['desktop', 1280, 720], ['mobile', 390, 844]]) {
        await page.setViewportSize({ width, height });
        for (const route of process.env.KG_EXPECT_BACKEND_OFFLINE ? ['login', 'learn'] : ['login']) {
          await page.goto(`http://${hostname}:3100/${route}`);
          if (route === 'learn') {
            await page.getByRole('alert').filter({ hasText: 'Không kết nối được máy chủ để làm mới phiên' }).waitFor();
            assert.equal(await page.getByRole('button', { name: 'Thử lại', exact: true }).isVisible(), true);
            assert.equal(await page.getByRole('link', { name: '← Quay lại đăng nhập', exact: true }).isVisible(), true);
          } else await page.getByRole('heading', { name: 'Đăng nhập', exact: true }).waitFor();
          await page.evaluate(() => document.fonts.ready);
          const size = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, viewport: innerWidth }));
          assert.ok(size.document <= size.viewport + 1);
          await page.screenshot({ path: path.join(output, `runtime-${device}-${route}.png`), fullPage: true });
          const scan = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
          report.views.push({ device, route, violations: scan.violations.map(v => ({ id: v.id, impact: v.impact })) });
        }
      }
    }
    await context.close();
  }
  assert.equal(report.pageErrors.length, 0);
  assert.equal(report.failedAssets.length, 0);
  assert.equal(report.views.filter(view => view.violations.length).length, 0);
  console.log(JSON.stringify(report, null, 2));
} finally {
  await browser.close();
  fs.writeFileSync(path.join(output, 'runtime-report.json'), JSON.stringify(report, null, 2));
}
