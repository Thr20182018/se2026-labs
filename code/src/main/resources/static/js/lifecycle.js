(() => {
  let submitting = false;
  const message = document.createElement('p');
  message.className = 'lifecycle-message';
  message.setAttribute('role', 'status');
  message.setAttribute('aria-live', 'polite');
  message.hidden = true;
  document.body.append(message);

  function showMessage(text, success) {
    message.className = `lifecycle-message ${success ? 'ok' : 'error'}`;
    message.textContent = text;
    message.hidden = false;
  }

  async function refreshMain() {
    const response = await fetch(window.location.href, {
      headers: { Accept: 'text/html' },
      credentials: 'same-origin',
      cache: 'no-store'
    });
    if (!response.ok || response.redirected) throw new Error('页面更新失败');
    const page = new DOMParser().parseFromString(await response.text(), 'text/html');
    const replacement = page.querySelector('main');
    const current = document.querySelector('main');
    if (!replacement || !current) throw new Error('页面更新失败');
    const expanded = new Map([...current.querySelectorAll('details[id]')]
      .map((details) => [details.id, details.open]));
    for (const details of replacement.querySelectorAll('details[id]')) {
      if (expanded.has(details.id)) details.open = expanded.get(details.id);
    }
    const scroll = { x: window.scrollX, y: window.scrollY };
    current.replaceWith(replacement);
    window.scrollTo(scroll.x, scroll.y);
  }

  document.addEventListener('submit', async (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || !form.matches('.lifecycle-form')) return;
    event.preventDefault();
    if (submitting) return;
    submitting = true;
    const body = new URLSearchParams(new FormData(form));
    const buttons = [...document.querySelectorAll('.lifecycle-form button:not(:disabled)')];
    const button = event.submitter || form.querySelector('button');
    const label = button?.textContent;
    buttons.forEach((item) => { item.disabled = true; });
    if (button) button.textContent = '提交中…';
    form.setAttribute('aria-busy', 'true');
    message.hidden = true;
    let resultMessage = null;
    try {
      const response = await fetch(form.getAttribute('action'), {
        method: 'POST',
        headers: { Accept: 'application/json' },
        body,
        credentials: 'same-origin'
      });
      if (response.redirected || response.status === 401) {
        throw new Error('登录已过期，请重新登录后再提交。');
      }
      if (!response.headers.get('Content-Type')?.includes('application/json')) {
        throw new Error(response.status === 403
          ? '操作被拒绝，请刷新页面后重试；若登录已失效，请重新登录。'
          : '暂时无法读取结果，请刷新页面确认最新状态。');
      }
      const result = await response.json();
      if (typeof result?.message !== 'string') throw new Error('返回结果无法识别，请刷新页面确认最新状态。');
      resultMessage = result.message;
      await refreshMain();
      showMessage(resultMessage, response.ok);
    } catch (error) {
      showMessage(resultMessage ? `${resultMessage} 页面更新失败，请刷新查看最新状态。`
        : error instanceof TypeError ? '网络连接失败，请刷新页面确认操作结果后重试。'
        : error instanceof SyntaxError ? '返回结果无法识别，请刷新页面确认最新状态。'
        : error.message, false);
    } finally {
      buttons.forEach((item) => { item.disabled = false; });
      if (button) button.textContent = label;
      form.removeAttribute('aria-busy');
      submitting = false;
    }
  });
})();
