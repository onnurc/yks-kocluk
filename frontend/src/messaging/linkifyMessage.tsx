import React from "react";

/**
 * Turns http(s) URLs inside a chat message into anchors, leaving everything else plain text.
 *
 * Deliberately narrow, because message content is user input and coaches now paste Google Meet
 * links here:
 * - Only `http://` and `https://` match. A pasted `javascript:`, `data:` or bare `www.` string
 *   stays inert text, so no scheme can ever become clickable.
 * - Output is a React node array, never `dangerouslySetInnerHTML` — React escapes every text
 *   node, so markup in a message can't become markup on the page.
 * - `rel="noopener noreferrer"` keeps the opened tab from reaching `window.opener` and from
 *   leaking the referrer.
 */
const URL_PATTERN = /https?:\/\/[^\s<>"']+/gi;

// A URL at the end of a sentence swallows the punctuation; trim what is almost never part of it.
const TRAILING_PUNCTUATION = /[.,;:!?)\]}'"]+$/;

export function linkifyMessage(content: string): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];
  let lastIndex = 0;
  let key = 0;

  for (const match of content.matchAll(URL_PATTERN)) {
    const raw = match[0];
    const start = match.index ?? 0;

    const trailing = TRAILING_PUNCTUATION.exec(raw)?.[0] ?? "";
    const url = trailing ? raw.slice(0, raw.length - trailing.length) : raw;

    // A match that is only a scheme ("https://") is not a usable link — leave it as text.
    if (!/^https?:\/\/[^/]/i.test(url)) continue;

    if (start > lastIndex) nodes.push(content.slice(lastIndex, start));
    nodes.push(
      <a key={`link-${key++}`} href={url} target="_blank" rel="noopener noreferrer">
        {url}
      </a>,
    );
    if (trailing) nodes.push(trailing);
    lastIndex = start + raw.length;
  }

  if (lastIndex < content.length) nodes.push(content.slice(lastIndex));
  return nodes;
}
