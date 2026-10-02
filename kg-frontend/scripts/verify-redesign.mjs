/* UI QA uses intercepted, explicitly synthetic API data. Never writes to the real backend.
 * Requires playwright and @axe-core/playwright (local install or NODE_PATH).
 * KG_UI_URL defaults to http://127.0.0.1:3100; KG_CHROME_PATH can override Chrome.
 */
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
const loadPackage = createRequire(import.meta.url);
const { chromium } = loadPackage('playwright');
const AxeBuilder = loadPackage('@axe-core/playwright').default;
const __dirname = fileURLToPath(new URL('.', import.meta.url));
const base = process.env.KG_UI_URL || 'http://127.0.0.1:3100';
const output = path.resolve(__dirname, '../.impeccable/review');
fs.mkdirSync(output, { recursive: true });
const user = { id: 'fixture-user', email: 'fixture@example.test', displayName: 'QA fixture', role: 'ADMIN' };
const topics = [
  { id: 'java', slug: 'java', name: 'Java', description: 'Nền tảng ngôn ngữ và máy ảo Java.', displayOrder: 1, moduleCount: 2 },
  { id: 'spring', slug: 'spring', name: 'Spring', description: 'Ứng dụng và giao dịch.', displayOrder: 2, moduleCount: 1 },
];
const modules = [
  { id: 'core', slug: 'java-core', name: 'Java Core', description: 'JDK, JRE, JVM và những khái niệm nền tảng.', displayOrder: 1, topicId: 'java', topicSlug: 'java', questionCount: 2 },
  { id: 'jvm', slug: 'jvm', name: 'Bộ nhớ JVM', description: 'Heap, stack và vòng đời đối tượng.', displayOrder: 2, topicId: 'java', topicSlug: 'java', questionCount: 1 },
  { id: 'transactions', slug: 'transactions', name: 'Transactions', description: 'Transaction isolation và rollback.', displayOrder: 3, topicId: 'spring', topicSlug: 'spring', questionCount: 1 },
];
const questions = [
  { id: 'jdk', moduleId: 'core', moduleSlug: 'java-core', title: 'Phân biệt JDK, JRE và JVM?', difficulty: 'JUNIOR', tags: ['runtime'], sortOrder: 1 },
  { id: 'equals', moduleId: 'core', moduleSlug: 'java-core', title: 'Khi nào cần ghi đè equals và hashCode?', difficulty: 'MID', tags: ['collections'], sortOrder: 2 },
];
const answer = '<h2>Ba vai trò khác nhau</h2><p>JVM thực thi bytecode. JRE gồm JVM và thư viện runtime. JDK cung cấp công cụ phát triển, bao gồm trình biên dịch javac.</p><div class="callout callout-info"><p class="callout-title">Điều cần nhớ</p><p>JDK dành cho phát triển; JVM thực thi chương trình.</p></div><pre><code>javac Hello.java\njava Hello</code></pre><script>window.__fixtureXss = true</script>';
const html = '<h2>Transaction isolation</h2><p>Isolation xác định cách các giao dịch đồng thời quan sát thay đổi dữ liệu.</p>';
const emptyPage = { items: [], page: 1, size: 10, totalElements: 0, totalPages: 1 };
function readPreviousReport() {
  try { return JSON.parse(fs.readFileSync(path.join(output, 'browser-report.json'), 'utf8')); }
  catch (error) { throw new Error('Cannot resume the owned QA report; run without KG_QA_RESUME to start a fresh pass.', { cause: error }); }
}
const results = process.env.KG_QA_RESUME ? readPreviousReport() : { fixtureNotice: 'All API data and write responses in these screenshots are synthetic browser fixtures, not production data or integration evidence.', checks: [], accessibility: [], pageErrors: [], unmatchedRequests: [], screenshots: [] };

async function main() {
  const browser = await chromium.launch({ headless: true, ...(process.env.KG_CHROME_PATH ? { executablePath: process.env.KG_CHROME_PATH } : process.platform === 'darwin' ? { executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' } : {}) });
  try {
    for (const [device, width, height] of [['desktop', 1440, 1000], ['mobile', 390, 844]]) {
      const context = await browser.newContext({ viewport: { width, height }, reducedMotion: 'reduce' });
      const page = await context.newPage();
      page.on('pageerror', error => results.pageErrors.push({ device, error: error.message }));
      let notes = [], posts = [{ id: 'post-draft', title: 'Transactions', slug: 'transactions', body: html, excerpt: 'Isolation trong cơ sở dữ liệu.', status: 'DRAFT', createdAt: '2026-10-02T08:00:00Z', tags: ['database'] }];
      let settings = { mode: 'POSTGRES', version: 1, updatedAt: '2026-10-02T08:00:00Z', updatedBy: user.id, elasticsearchConfigured: true };
      let failSearch = false, denyRefresh = false;
      const writes = [];
      const token = { accessToken: 'fixture-only-token', expiresIn: 3600, user };
      let profile = { ...user, authProvider: 'LOCAL', avatarUrl: null, stats: { xp: 1240, currentStreak: 8, longestStreak: 10, level: 3, badges: [] } };
      let failUpload = false;
      const avatar = '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="#dce7d9"/><text x="40" y="54" text-anchor="middle" fill="#273c4a" font-size="42">Q</text></svg>';
      await page.route('https://uploads.fixture.test/**', route => route.fulfill({ status: failUpload ? 500 : 200, headers: { 'access-control-allow-origin': base, 'access-control-allow-methods': 'PUT,OPTIONS', 'access-control-allow-headers': 'Content-Type' }, body: '' }));
      await page.route('**/qa-avatar.svg', route => route.fulfill({ status: 200, contentType: 'image/svg+xml', body: avatar }));
      const interview = { id: 'interview-fixture', topicId: 'java', questionCount: 1, mode: 'TEXT', status: 'ACTIVE', overallScore: null, startedAt: '2026-10-02T08:00:00Z', finishedAt: null, questionIds: ['jdk'] };
      await page.route('**/api/v1/**', async route => {
        const request = route.request(), url = new URL(request.url()), endpoint = url.pathname.replace('/api/v1', ''), method = request.method();
        const headers = { 'access-control-allow-origin': base, 'access-control-allow-credentials': 'true', 'access-control-allow-headers': 'Authorization,Content-Type', 'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS' };
        if (method === 'OPTIONS') return route.fulfill({ status: 204, headers });
        const body = request.postData() ? request.postDataJSON() : null;
        if (method !== 'GET') writes.push({ endpoint, method, body });
        let data;
        if (endpoint === '/auth/refresh' && denyRefresh) return route.fulfill({ status: 401, headers, json: { detail: 'Fixture session expired' } });
        if (endpoint === '/auth/login' || endpoint === '/auth/refresh' || endpoint === '/auth/register') data = token;
        else if (endpoint === '/auth/forgot-password') data = { message: 'Fixture code sent' };
        else if (endpoint === '/auth/reset-password') data = { message: 'Fixture password reset' };
        else if (endpoint === '/auth/logout') return route.fulfill({ status: 204, headers });
        else if (endpoint === '/topics') data = topics;
        else if (endpoint === '/modules') data = modules;
        else if (endpoint === '/questions') {
          const filtered = questions.filter(q => (!url.searchParams.get('moduleId') || q.moduleId === url.searchParams.get('moduleId')) && (!url.searchParams.get('q') || q.title.toLowerCase().includes(url.searchParams.get('q').toLowerCase())) && (!url.searchParams.get('difficulty') || q.difficulty === url.searchParams.get('difficulty')) && (!url.searchParams.get('tag') || q.tags.includes(url.searchParams.get('tag'))));
          data = { ...emptyPage, items: filtered, size: 20, totalElements: filtered.length };
        } else if (endpoint.startsWith('/questions/')) data = { ...questions.find(q => q.id === endpoint.split('/').at(-1)), answerHtml: answer, options: [] };
        else if (endpoint === '/srs/enroll') data = { enrolled: 1, cardIds: ['card-fixture'], deckId: null };
        else if (endpoint === '/srs/due') data = [{ cardId: 'card-fixture', questionId: 'jdk', moduleId: 'core', moduleSlug: 'java-core', title: questions[0].title, answerHtml: answer, difficulty: 'JUNIOR', repetitions: 0, easeFactor: 2.5, intervalDays: 1, nextReview: '2026-10-03' }];
        else if (endpoint.startsWith('/srs/review/')) data = { cardId: 'card-fixture', quality: body.quality, intervalDays: 1, easeFactor: 2.5, repetitions: 1, nextReview: '2026-10-03', correct: body.quality >= 2 };
        else if (endpoint === '/quiz/history' || endpoint === '/mock-interview/history') data = emptyPage;
        else if (endpoint === '/quiz/generate') data = { id: 'quiz-fixture', strategy: body.strategy, score: null, total: 1, startedAt: '2026-10-02T08:00:00Z', finishedAt: null, timeLimit: 900, questions: [{ questionId: 'jdk', title: 'JVM có vai trò gì?', options: [{ id: 'correct', content: 'Thực thi bytecode' }, { id: 'wrong', content: 'Biên dịch mã nguồn Java' }] }] };
        else if (endpoint === '/quiz/quiz-fixture/submit') data = { sessionId: 'quiz-fixture', score: 100, correctCount: 1, total: 1, breakdown: [{ questionId: 'jdk', correct: true, selectedOptionId: 'correct', correctOptionId: 'correct' }] };
        else if (endpoint === '/mock-interview/start') data = { session: interview, questions: [{ questionId: 'jdk', title: questions[0].title }] };
        else if (endpoint === '/mock-interview/interview-fixture/answer') data = { questionId: 'jdk', userAnswer: body.userAnswer, keywordScore: 80, feedback: 'Đã nhắc đến vai trò thực thi bytecode.', sampleAnswer: 'JVM thực thi bytecode.' };
        else if (endpoint === '/mock-interview/interview-fixture/finish') data = { ...interview, status: 'FINISHED', overallScore: 80, finishedAt: '2026-10-02T09:00:00Z' };
        else if (endpoint === '/notes' && method === 'POST') { const note = { id: 'note-fixture', ...body }; notes.push(note); data = note; }
        else if (endpoint === '/notes') data = notes;
        else if (endpoint === '/users/me') { if (method === 'PATCH') profile = { ...profile, ...body }; data = profile; }
        else if (endpoint === '/storage/presigned-put') data = { bucket: 'kg-avatars', objectKey: 'fixture-avatar', url: 'https://uploads.fixture.test/avatar', publicUrl: base + '/qa-avatar.svg', expiresInSeconds: 300 };
        else if (endpoint === '/users/me/bookmarks') data = [];
        else if (endpoint === '/search') data = [];
        else if (endpoint === '/dashboard/radar') data = { modules: modules.map((m, i) => ({ moduleId: m.id, name: m.name, masteryPct: [58, 32, 71][i] })) };
        else if (endpoint === '/dashboard/heatmap') data = [{ date: '2026-10-02', count: 4 }];
        else if (endpoint === '/users/me/progress') data = [];
        else if (endpoint === '/users/me/stats') data = { xp: 1240, currentStreak: 8, longestStreak: 10, level: 3, badges: [] };
        else if (endpoint === '/dashboard/leaderboard') data = [{ rank: 1, userId: user.id, displayName: 'QA fixture', xp: 1240 }];
        else if (endpoint === '/mindmap') data = { nodes: modules.map((m, i) => ({ ...m, masteryPct: [58, 32, 71][i] })), edges: [{ source: 'core', target: 'jvm', relation: 'topic' }] };
        else if (endpoint === '/admin/search/elasticsearch/status') data = { output: 'Fixture Elasticsearch running' };
        else if (endpoint === '/admin/search/elasticsearch/start') data = { output: 'Elasticsearch đã khởi động.' };
        else if (endpoint === '/admin/search/elasticsearch/stop') { settings = { ...settings, mode: 'POSTGRES', version: settings.version + 1 }; data = settings; }
        else if (endpoint === '/admin/search/settings') {
          if (failSearch && method === 'GET') { failSearch = false; return route.fulfill({ status: 503, headers, json: { detail: 'Không tải được cấu hình search' } }); }
          if (method === 'PUT') settings = { ...settings, mode: body.mode, version: body.version + 1 };
          data = settings;
        } else if (endpoint === '/admin/blog/writer/settings') data = { settings: { enabled: false, localTime: '06:00:00', timezone: 'Asia/Jakarta', dailyLimit: 1, policy: 'MANUAL_REVIEW', qualityThreshold: 85, lastScheduledDate: null, lastRunAt: null }, apiKeyConfigured: true, writerEnabled: true, nextRunAt: null };
        else if (endpoint === '/admin/blog/writer/review') data = posts;
        else if (endpoint === '/admin/blog/writer/stats') data = { stats: { pendingReview: posts.length, generatedToday: 2, tokensToday: 1450, costToday: 0.003, costThisMonth: 0.42, failedJobs: 0 }, monthlyCostAlertUsd: 10 };
        else if (endpoint === '/admin/blog/writer/posts/post-draft/revisions') data = [{ id: 'revision-fixture', version: 1, title: 'Transactions', body: html, excerpt: '', qualityScore: 87, tokensUsed: 725, costUsd: 0.0015, createdAt: '2026-10-02T08:00:00Z', instruction: null }];
        else if (endpoint === '/admin/blog/writer/posts/post-draft/publish') { posts = []; data = {}; }
        else if (endpoint === '/blog/posts') data = [{ ...posts[0], id: 'published-fixture', slug: 'transactions', title: 'Transaction isolation', excerpt: 'Các mức cô lập trong cơ sở dữ liệu.', tags: ['database'], publishedAt: '2026-10-02T08:00:00Z', viewCount: 12, likeCount: 3 }];
        else if (endpoint === '/blog/posts/transactions') data = { id: 'published-fixture', title: 'Transaction isolation', slug: 'transactions', body: html, excerpt: null, publishedAt: '2026-10-02T08:00:00Z', viewCount: 12, likeCount: 3, tags: ['database'] };
        else if (endpoint === '/blog/posts/transactions/comments') data = [];
        else if (endpoint === '/blog/posts/transactions/view') data = {};
        else { results.unmatchedRequests.push({ device, method, endpoint }); return route.fulfill({ status: 404, headers, json: { detail: 'Unhandled fixture route' } }); }
        return route.fulfill({ status: 200, headers, json: data });
      });
      const check = label => { if (!results.checks.some(item => item.device === device && item.label === label)) results.checks.push({ device, label }); console.log('PASS', device, label); };
      const capture = async name => {
        if (process.env.KG_QA_RESUME && results.screenshots.includes(`${device}-${name}.png`)) return;
        await page.evaluate(() => document.fonts.ready);
        await page.screenshot({ path: path.join(output, `${device}-${name}.png`), fullPage: true });
        results.screenshots.push(`${device}-${name}.png`);
        const sizes = await page.evaluate(() => ({ viewport: window.innerWidth, document: document.documentElement.scrollWidth }));
        assert.ok(sizes.document <= sizes.viewport + 1, `${device} ${name} has horizontal document overflow ${JSON.stringify(sizes)}`);
        const a11y = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        results.accessibility.push({ device, name, violations: a11y.violations.map(v => ({ id: v.id, impact: v.impact, description: v.description, nodes: v.nodes.map(n => ({ html: n.html, failureSummary: n.failureSummary })) })) });
      };
      await page.goto(base + '/register');
      await page.getByRole('heading', { name: 'Tạo tài khoản', exact: true }).waitFor();
      await capture('register');
      await page.getByLabel('Tên hiển thị', { exact: true }).fill('QA fixture');
      await page.getByLabel('Email', { exact: true }).fill(user.email);
      await page.getByLabel('Mật khẩu', { exact: true }).fill('fixture-password');
      await page.getByRole('button', { name: 'Đăng ký', exact: true }).click();
      await page.waitForURL('**/learn');
      check('registration uses original token contract and topic-first destination');
      await page.goto(base + '/forgot-password');
      await page.getByRole('heading', { name: 'Quên mật khẩu', exact: true }).waitFor();
      await capture('forgot-password');
      await page.getByLabel('Email', { exact: true }).fill(user.email);
      await page.getByRole('button', { name: 'Gửi mã', exact: true }).click();
      await page.getByLabel('Mã 6 số', { exact: true }).fill('123456');
      await page.getByRole('button', { name: 'Tiếp tục', exact: true }).click();
      await page.getByLabel('Mật khẩu mới', { exact: true }).fill('fixture-new-password');
      await page.getByRole('button', { name: 'Đặt lại mật khẩu', exact: true }).click();
      await page.getByText('Fixture password reset', { exact: false }).waitFor();
      assert.deepEqual(writes.find(w => w.endpoint === '/auth/reset-password').body, { email: user.email, code: '123456', newPassword: 'fixture-new-password' });
      check('password recovery preserves six-digit code and reset payload');
      await page.goto(base + '/login');
      await page.getByRole('heading', { name: 'Đăng nhập', exact: true }).waitFor();
      await capture('login');
      await page.getByLabel('Email', { exact: true }).fill(user.email);
      await page.getByLabel('Mật khẩu', { exact: true }).fill('fixture-password');
      await page.getByRole('button', { name: 'Vào phòng tập', exact: true }).click();
      await page.waitForURL('**/learn');
      await page.getByRole('button', { name: 'Java', exact: true }).click();
      await page.getByRole('button', { name: /Java Core.*2 câu hỏi/ }).click();
      await page.getByRole('link', { name: 'Đọc & khám phá', exact: true }).waitFor();
      check('login leads to topic-first entry; selecting module reveals practice actions');
      await capture('learn');
      await page.getByLabel('Tìm chủ đề hoặc module').fill('bo nho');
      await page.getByRole('button', { name: /Bộ nhớ JVM.*1 câu hỏi/ }).waitFor();
      assert.equal(await page.getByRole('button', { name: /Java Core.*2 câu hỏi/ }).count(), 0);
      check('accent-insensitive topic search');
      await page.getByLabel('Tìm chủ đề hoặc module').fill('');
      await page.getByRole('button', { name: 'English', exact: true }).click();
      await page.getByRole('heading', { name: 'What would you like to practise?' }).waitFor();
      await page.reload();
      await page.getByRole('heading', { name: 'What would you like to practise?' }).waitFor();
      assert.equal(await page.evaluate(() => document.documentElement.lang), 'en');
      check('English preference survives reload and updates document language');
      await capture('learn-en');
      await page.goto(base + '/questions?moduleId=core');
      await page.getByRole('link', { name: /Phân biệt JDK/ }).waitFor();
      await page.getByPlaceholder('Try heap, memory, equals…').fill('JDK');
      await page.getByRole('button', { name: 'Search', exact: true }).click();
      await page.getByRole('link', { name: /Phân biệt JDK/ }).waitFor();
      await page.waitForFunction(() => !document.querySelector('main')?.textContent.includes('Khi nào cần ghi đè'));
      check('question search preserves module deep link and filtering');
      await capture('questions-en');
      await page.getByRole('button', { name: 'Tiếng Việt', exact: true }).click();
      await page.getByRole('link', { name: /Phân biệt JDK/ }).click();
      await page.locator('.answer-html h2').waitFor();
      assert.equal(await page.locator('.answer-html script').count(), 0);
      assert.equal(await page.evaluate(() => window.__fixtureXss), undefined);
      check('answer HTML remains sanitized');
      await capture('answer');
      await page.goto(base + '/notes');
      await page.getByLabel('Nội dung ghi chú').fill('## Nội dung fixture\nJVM thực thi bytecode.');
      await page.getByRole('button', { name: 'Lưu ghi chú', exact: true }).click();
      await page.waitForFunction(() => document.querySelector('main')?.textContent.includes('## Nội dung fixture'));
      assert.equal(notes[0].content, '## Nội dung fixture\nJVM thực thi bytecode.');
      check('notes save uses unchanged API content');
      await capture('notes');
      await page.goto(base + '/profile');
      await page.getByLabel('Tên hiển thị', { exact: true }).waitFor();
      await capture('profile');
      await page.getByLabel('Tên hiển thị', { exact: true }).fill('QA updated');
      await page.getByRole('button', { name: 'Lưu hồ sơ', exact: true }).click();
      await page.getByText('Đã lưu hồ sơ.', { exact: true }).waitFor();
      assert.equal(await page.evaluate(() => JSON.parse(sessionStorage.getItem('kg.user')).displayName), 'QA updated');
      assert.ok((await page.locator('header').first().textContent()).includes('QA updated'));
      check('profile PATCH updates display name and shell immediately');
      failUpload = true;
      const patchesBefore = writes.filter(w => w.endpoint === '/users/me' && w.method === 'PATCH').length;
      const file = { name: 'avatar.svg', mimeType: 'image/svg+xml', buffer: Buffer.from(avatar) };
      await page.locator('input[type="file"]').setInputFiles(file);
      await page.getByRole('alert').filter({ hasText: 'Upload failed with HTTP 500' }).waitFor();
      assert.equal(await page.getByRole('button', { name: 'Đổi ảnh đại diện', exact: true }).isEnabled(), true);
      assert.equal(writes.filter(w => w.endpoint === '/users/me' && w.method === 'PATCH').length, patchesBefore);
      failUpload = false;
      await page.locator('input[type="file"]').setInputFiles(file);
      await page.getByText('Đã lưu hồ sơ.', { exact: true }).waitFor();
      assert.equal(profile.avatarUrl, base + '/qa-avatar.svg');
      assert.equal(await page.getByRole('button', { name: 'Đổi ảnh đại diện', exact: true }).isEnabled(), true);
      check('avatar upload persists its URL and clears busy on failure/retry');
      await page.getByRole('button', { name: 'English', exact: true }).click();
      await page.getByRole('heading', { name: 'Your profile', exact: true }).waitFor();
      await capture('profile-en');
      await page.getByRole('button', { name: 'Tiếng Việt', exact: true }).click();
      await page.goto(base + '/flashcard/core');
      await page.getByRole('button', { name: 'Lật xem đáp án', exact: true }).waitFor();
      await capture('flashcard');
      await page.getByRole('button', { name: 'Lật xem đáp án', exact: true }).click();
      await page.keyboard.press('3');
      await page.getByRole('heading', { name: 'Hết thẻ trong phiên', exact: true }).waitFor();
      assert.equal(writes.find(w => w.endpoint.startsWith('/srs/review/')).body.quality, 2);
      check('flashcard keyboard rating keeps quality 0–3 mapping');
      await page.goto(base + '/quiz/core');
      await page.getByRole('button', { name: 'Bắt đầu quiz', exact: true }).click();
      await page.getByRole('radio', { name: 'Thực thi bytecode', exact: true }).check();
      await page.getByRole('button', { name: 'Nộp bài', exact: true }).click();
      await page.getByText('Kết quả:', { exact: false }).waitFor();
      assert.equal(writes.find(w => w.endpoint.endsWith('/submit')).body.answers[0].selectedOptionId, 'correct');
      check('quiz generation and submission preserve answer contract');
      await capture('quiz');
      await page.goto(base + '/mock-interview');
      await page.getByRole('button', { name: 'Bắt đầu', exact: true }).click();
      await page.getByLabel('Câu trả lời của bạn').fill('JVM thực thi bytecode.');
      await page.getByRole('button', { name: 'Lưu và chấm', exact: true }).click();
      await page.getByText('Điểm từ khóa:', { exact: false }).waitFor();
      await page.getByRole('button', { name: 'Kết thúc phỏng vấn', exact: true }).click();
      await page.getByText('Điểm trung bình:', { exact: false }).waitFor();
      check('interview answer and finish contracts preserved');
      await capture('interview');
      for (const route of ['dashboard', 'mindmap', 'blog']) {
        await page.goto(base + '/' + route);
        await page.locator('main h1').waitFor();
        if (route === 'dashboard') await page.getByText('1.240 XP').first().waitFor();
        if (route === 'mindmap') await page.locator('svg [role="button"]').first().waitFor();
        if (route === 'blog') await page.getByRole('link', { name: 'Transaction isolation', exact: true }).waitFor();
        await capture(route);
      }
      await page.goto(base + '/blog/transactions');
      await page.locator('.answer-html h2').waitFor();
      await capture('article');
      await page.goto(base + '/admin/search');
      await page.getByRole('radio', { name: /^PostgreSQL/ }).waitFor();
      await capture('search-admin');
      await page.getByRole('radio', { name: /Tự động/ }).check();
      page.once('dialog', dialog => dialog.accept());
      await page.getByRole('button', { name: 'Lưu cấu hình', exact: true }).click();
      await page.getByText('Đã cập nhật search backend.').waitFor();
      const savedSearch = writes.find(w => w.endpoint === '/admin/search/settings' && w.method === 'PUT');
      assert.deepEqual(savedSearch.body, { mode: 'AUTO', version: 1 });
      failSearch = true;
      await page.reload();
      await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
      await page.getByRole('radio', { name: /Tự động/ }).waitFor();
      check('search admin confirmation, optimistic version and error/retry');
      await page.getByRole('button', { name: 'Trạng thái ES', exact: true }).click();
      await page.getByText('Fixture Elasticsearch running', { exact: true }).waitFor();
      await page.getByRole('button', { name: 'Bật ES', exact: true }).click();
      await page.getByText('Elasticsearch đã khởi động.', { exact: true }).waitFor();
      page.once('dialog', dialog => dialog.dismiss());
      await page.getByRole('button', { name: 'Tắt ES', exact: true }).click();
      assert.equal(writes.filter(w => w.endpoint.endsWith('/elasticsearch/stop')).length, 0);
      page.once('dialog', dialog => dialog.accept());
      await page.getByRole('button', { name: 'Tắt ES', exact: true }).click();
      await page.getByText('Elasticsearch đã dừng; search chuyển về PostgreSQL.', { exact: true }).waitFor();
      assert.equal(await page.getByRole('radio', { name: /^PostgreSQL/ }).isChecked(), true);
      settings = { ...settings, elasticsearchConfigured: false };
      await page.reload();
      await page.getByRole('radio', { name: /Tự động/ }).waitFor();
      assert.equal(await page.getByRole('radio', { name: /Tự động/ }).isDisabled(), true);
      assert.equal(await page.getByRole('radio', { name: /^Elasticsearch/ }).isDisabled(), true);
      check('ES lifecycle is preserved; stop needs confirmation; unavailable engines stay disabled');
      await page.getByRole('button', { name: 'English', exact: true }).click();
      await page.getByRole('heading', { name: 'Search settings', exact: true }).waitFor();
      await capture('search-admin-en');
      await page.getByRole('button', { name: 'Tiếng Việt', exact: true }).click();
      await page.goto(base + '/admin/writer');
      await page.getByRole('button', { name: /Transactions/ }).click();
      await page.getByRole('heading', { name: 'Biên tập bài viết', exact: true, level: 2 }).waitFor();
      await capture('writer-admin');
      await page.getByRole('button', { name: 'English', exact: true }).click();
      await page.getByRole('heading', { name: 'Content administration', exact: true }).waitFor();
      await capture('writer-admin-en');
      await page.getByRole('button', { name: 'Tiếng Việt', exact: true }).click();
      page.once('dialog', dialog => dialog.accept());
      await page.getByRole('button', { name: 'Duyệt & xuất bản', exact: true }).click();
      await page.getByText('Đã xuất bản bài viết.').waitFor();
      assert.ok(writes.some(w => w.endpoint === '/admin/blog/writer/posts/post-draft/publish'));
      check('writer review loads sanitized preview and confirms publishing');
      if (device === 'mobile') {
        await page.getByRole('button', { name: 'Mở điều hướng', exact: true }).click();
        await page.getByRole('link', { name: 'Chọn chủ đề', exact: true }).click();
        await page.waitForURL('**/learn');
        assert.equal(await page.getByRole('button', { name: 'Mở điều hướng', exact: true }).getAttribute('aria-expanded'), 'false');
        check('mobile navigation closes on route selection');
      }
      await page.goto(base + '/learn');
      await page.getByRole('button', { name: /Java Core.*2 câu hỏi/ }).click();
      await page.setViewportSize(device === 'desktop' ? { width: 1280, height: 720 } : { width: 320, height: 700 });
      await capture(device === 'desktop' ? 'learn-laptop' : 'learn-narrow');
      await page.setViewportSize({ width, height });
      check('topic-first layout also holds at small laptop/narrow mobile sizes');
      await page.evaluate(({ user }) => sessionStorage.setItem('kg.user', JSON.stringify({ ...user, role: 'USER' })), { user });
      await page.goto(base + '/learn');
      await page.getByRole('heading', { name: 'Bạn muốn học gì hôm nay?', exact: true }).waitFor();
      assert.equal(await page.locator('a[href="/admin/writer"], a[href="/admin/search"]').count(), 0);
      check('learner navigation excludes administrator links');
      denyRefresh = true;
      await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
      await page.waitForURL('**/login');
      await page.goto(base + '/learn');
      await page.waitForURL('**/login');
      check('logout and denied refresh do not bypass authentication');
      await context.close();
    }
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(output, 'browser-report.json'), JSON.stringify(results, null, 2));
  }
  assert.equal(results.pageErrors.length, 0, JSON.stringify(results.pageErrors));
  assert.equal(results.unmatchedRequests.length, 0, JSON.stringify(results.unmatchedRequests));
  const violations = results.accessibility.filter(item => item.violations.length);
  console.log(JSON.stringify({ checks: results.checks.length, screenshots: results.screenshots.length, accessibilityScreens: results.accessibility.length, accessibilityFailures: violations.length, output }, null, 2));
  assert.equal(violations.length, 0, 'Accessibility failures: see browser-report.json');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
