(() => {
  const list = document.querySelector('.sessions');
  if (!list) return;

  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const sessions = [...list.querySelectorAll('.session')].map((element) => ({
    element,
    summary: element.querySelector('.session-summary'),
    content: element.querySelector('.session-content'),
    expanded: element.open,
    animation: null
  }));

  function finish(session) {
    session.animation?.cancel();
    session.animation = null;
    session.element.open = session.expanded;
    session.element.style.height = '';
    session.element.style.overflow = '';
  }

  function setExpanded(session, expanded) {
    if (session.expanded === expanded) return;
    const startHeight = session.element.getBoundingClientRect().height;
    session.animation?.cancel();
    session.expanded = expanded;
    session.summary.setAttribute('aria-expanded', String(expanded));
    session.content.inert = !expanded;

    if (reducedMotion.matches || !session.element.animate) {
      finish(session);
      return;
    }

    // Keep closing content rendered until the height transition completes.
    session.element.style.height = `${startHeight}px`;
    session.element.style.overflow = 'hidden';
    session.element.open = true;
    const endHeight = session.summary.getBoundingClientRect().height
      + (expanded ? session.content.getBoundingClientRect().height : 0);
    const animation = session.element.animate(
      { height: [`${startHeight}px`, `${endHeight}px`] },
      { duration: 300, easing: 'cubic-bezier(.22,.8,.2,1)' }
    );
    session.animation = animation;
    animation.onfinish = () => {
      if (session.animation === animation) finish(session);
    };
  }

  function close(session) {
    session.summary.focus({ preventScroll: true });
    setExpanded(session, false);
  }

  async function apply(session, form) {
    const button = form.querySelector('button');
    if (button.disabled) return;
    const message = session.content.querySelector('.registration-message');
    const login = session.content.querySelector('.registration-login');
    let canApply = true;
    let actionLabel = button.textContent;
    button.disabled = true;
    button.textContent = '提交中…';
    form.setAttribute('aria-busy', 'true');
    message.className = 'registration-message';
    message.textContent = '正在提交申请…';
    login.hidden = true;
    try {
      const response = await fetch(form.getAttribute('action'), {
        method: 'POST',
        headers: { Accept: 'application/json' },
        body: new URLSearchParams(new FormData(form)),
        credentials: 'same-origin'
      });
      if (response.redirected || response.status === 401) {
        login.hidden = false;
        throw new Error('登录已过期，请重新登录后再提交。');
      }
      if (!response.headers.get('Content-Type')?.includes('application/json')) {
        login.hidden = response.status !== 403;
        throw new Error(response.status === 403
          ? '操作被拒绝，请刷新页面后重试；若登录已失效，请重新登录。'
          : '暂时无法读取结果，请刷新页面查看报名状态后重试。');
      }
      const result = await response.json();
      if (!result || !['OPEN', 'WAITLIST', 'CLOSED', 'CANCELLED'].includes(result.availability)
          || typeof result.canApply !== 'boolean' || !Number.isInteger(result.remaining)) {
        throw new Error('返回结果无法识别，请刷新页面查看报名状态。');
      }
      const availability = session.summary.querySelector('.status');
      availability.className = `status ${result.availability}`;
      availability.textContent = result.availabilityLabel;
      session.content.querySelector('.session-remaining').textContent = result.remaining;
      const status = form.querySelector('.registration-status');
      status.textContent = result.statusLabel;
      status.hidden = !result.status;
      form.elements.namedItem('action').value = result.action;
      canApply = result.canApply;
      actionLabel = result.actionLabel;
      message.textContent = result.message;
      message.classList.add(response.ok ? 'ok' : 'error');
    } catch (error) {
      message.textContent = error instanceof TypeError
        ? '网络连接失败，请检查连接，并刷新页面确认报名结果后重试。'
        : error instanceof SyntaxError ? '返回结果无法识别，请刷新页面查看报名状态。' : error.message;
      message.classList.add('error');
    } finally {
      button.disabled = !canApply;
      button.textContent = actionLabel;
      form.removeAttribute('aria-busy');
    }
  }

  for (const session of sessions) {
    session.summary.setAttribute('aria-expanded', String(session.expanded));
    session.content.inert = !session.expanded;
    session.summary.addEventListener('click', (event) => {
      event.preventDefault();
      const expanded = !session.expanded;
      if (expanded) {
        for (const other of sessions) {
          if (other !== session) setExpanded(other, false);
        }
      }
      setExpanded(session, expanded);
    });
    session.content.querySelector('.session-close').addEventListener('click', () => close(session));
    const form = session.content.querySelector('.registration-form');
    form?.addEventListener('submit', (event) => {
      event.preventDefault();
      apply(session, form);
    });
    session.element.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && session.expanded) {
        event.preventDefault();
        close(session);
      }
    });
  }

  // A changed layout needs natural heights, not the previous width's measurements.
  const finishAnimations = () => sessions.filter((session) => session.animation).forEach(finish);
  window.addEventListener('resize', finishAnimations);
  reducedMotion.addEventListener('change', finishAnimations);
  list.classList.add('has-session-interactions');
})();
