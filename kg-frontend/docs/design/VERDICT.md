## verdict

- Resolved — transport error copy: the fresh `runtime-mobile-learn.png` visibly states “Không kết nối được máy chủ để làm mới phiên” and offers retry/back-to-login. Known browser failures also have VI/EN translations and a new unit test. Authentication was not bypassed.
- Resolved — main landmark: AuthShell and full-page session loading/recovery states now use main. Fresh real login Lighthouse reports `landmark-one-main: 1` and accessibility 100/100; login remains visually unchanged in `runtime-desktop-login.png`.
- No captured regression from the fix batch: 39 fixture interactions and 44 recaptured fixture states pass; four additional real runtime captures pass. Deprecated icon names were replaced by supported Icon-suffix exports without changing the rendered icons or labels.

## remaining

Clear for the two scored UI fixes. This direct verdict is not independent whole-surface approval, WCAG certification or live integration approval. Backend port 8080 is offline; real authentication, storage, search workers and SRS remain unverified. The subsequent owner-requested dependency remediation reduced the former eleven npm findings to zero; fresh Next 15 login LCP is 2.3s rather than the earlier 2.9s sample. See SECURITY-REMEDIATION.md. This separate remediation does not turn the original UI verdict into independent security approval.

disposition: ship

Ship covers the scored fixes, not the whole surface.
