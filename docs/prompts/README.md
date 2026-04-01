# Reusable Prompts

Copy-paste these prompts into Cursor or ChatGPT when needed.

| Prompt | Use in | Purpose |
|--------|--------|---------|
| **SEO_LANDING_PAGE_AUDIT_PROMPT.md** | Cursor (or any AI) | Extract every user-facing feature, string, screen, and marketable element from the app codebase for SEO and landing page content. Output: strings, screens & flows, features, settings, technical differentiators, raw keyword seeds. |
| **KEYWORD_RESEARCH_DEEP_RESEARCH_PROMPT.md** | ChatGPT (Deep Research / web search) | After extraction: get primary keyword, 10–15 secondary keywords, 5–10 FAQ-style keywords, and short justification (volume/competition) for a landing page that ranks and sends traffic to the app store. Paste your extraction output into the placeholder. |
| **LANDING_PAGE_CONTENT_GENERATION_PROMPT.md** | ChatGPT (Deep Search or normal) | After keyword research: generate full landing page content (YAML frontmatter + 800–1200 words markdown) for Astro. SEO-optimized: primary keyword in H1 and first 100 words, secondary in H2s/body, FAQ keywords as faq entries. Copy-paste ready for `src/content/apps/[slug].md`. |

**Typical workflow (3 steps):**
1. **Cursor:** Run SEO audit prompt → get full extraction (features, screens, keyword seeds).
2. **ChatGPT Deep Research:** Paste extraction into keyword research prompt → get primary keyword, secondary keywords, FAQ-style keywords, justification.
3. **ChatGPT:** Paste keyword research into landing page content prompt → get YAML frontmatter + body markdown for your Astro app page.
