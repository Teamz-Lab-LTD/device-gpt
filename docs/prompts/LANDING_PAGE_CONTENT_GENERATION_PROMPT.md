# Landing Page Content Generation Prompt (Reusable)

**Use in:** ChatGPT (Deep Search or normal) **after** you have keyword research. Paste your primary keyword, secondary keywords, and FAQ-style keywords below. Output is copy-paste ready for `src/content/apps/[slug].md` (Astro).

Copy-paste the block below into ChatGPT. Replace the placeholders with your app name, store, description, and keyword research output.

---

I have SEO keyword research for my app landing page. Generate the full landing page content so the page is highly searchable and matches search intent.

**App name:** [Your app name]

**Store:** [Google Play / App Store / both]

**One-line app description:** [One sentence]

**Keyword research (from deep research):**
- Primary keyword: [paste]
- Secondary keywords: [paste full list]
- FAQ-style keywords: [paste full list]

**Requirements:**
1. **Frontmatter (YAML):** Generate all required fields for a static site (Astro). Schema includes: appName, tagline, shortDescription, longDescription, primaryKeyword, secondaryKeywords (array), category, hero (headline, subheadline, primaryCTA, secondaryCTA), screenshots (array of paths), featureBlocks (title, description, iconName), howItWorksSteps (title, description), faq (q, a), privacy (dataSafetySummary, permissionsSummary), playStoreUrl / appStoreUrl, teamzLabCTA. Use placeholder URLs if needed.
2. **SEO:** Use the primary keyword in the first 100 words and in the hero headline (the only H1). Weave secondary keywords naturally into subheadings (H2) and body. Include all FAQ-style keywords as faq entries (q and a). No keyword stuffing; people-first, helpful copy.
3. **Body copy:** 800–1200 words of markdown below the frontmatter. Structure with 2–4 H2 sections. Cover features from the keyword list. One H1 total (the hero headline).
4. **Tone:** Helpful, clear, conversion-focused (goal: click to store). No hype or generic fluff.

Output format: first the complete YAML frontmatter (between ---), then the body markdown. Copy-paste ready for src/content/apps/[slug].md.
