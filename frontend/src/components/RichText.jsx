// Minimal renderer for the Markdown subset used in question prompts: paragraphs, **bold**,
// `inline code` and ``` fenced code blocks. Everything is emitted as React text nodes (never HTML),
// so prompt content cannot inject markup.

function renderInline(text) {
  return text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).map((part, index) => {
    if (part.length > 4 && part.startsWith('**') && part.endsWith('**')) {
      return <strong key={index}>{part.slice(2, -2)}</strong>;
    }
    if (part.length > 2 && part.startsWith('`') && part.endsWith('`')) {
      return (
        <code key={index} className="rounded bg-slate-100 px-1 py-0.5 font-mono text-[0.85em] text-slate-800">
          {part.slice(1, -1)}
        </code>
      );
    }
    return part;
  });
}

function parseBlocks(text, preserveLines) {
  const blocks = [];
  let paragraph = [];
  let code = null;

  const flushParagraph = () => {
    if (paragraph.length > 0) {
      blocks.push({ type: 'paragraph', text: paragraph.join(preserveLines ? '\n' : ' ') });
      paragraph = [];
    }
  };

  for (const line of text.split(/\r?\n/)) {
    if (line.trim().startsWith('```')) {
      if (code === null) {
        flushParagraph();
        code = [];
      } else {
        blocks.push({ type: 'code', text: code.join('\n') });
        code = null;
      }
    } else if (code !== null) {
      code.push(line);
    } else if (line.trim() === '') {
      flushParagraph();
    } else {
      paragraph.push(line.trim());
    }
  }
  if (code !== null) {
    blocks.push({ type: 'code', text: code.join('\n') });
  }
  flushParagraph();
  return blocks;
}

/** `preserveLines` keeps single line breaks inside a paragraph (numbered steps, bullet lines). */
export default function RichText({ text, preserveLines = false }) {
  const blocks = parseBlocks(text || '', preserveLines);
  return (
    <div className="space-y-3 text-sm leading-relaxed text-slate-800">
      {blocks.map((block, index) =>
        block.type === 'code' ? (
          <pre key={index} className="overflow-x-auto rounded-md bg-slate-900 p-3 font-mono text-xs leading-relaxed text-slate-100">
            <code>{block.text}</code>
          </pre>
        ) : (
          <p key={index} className={preserveLines ? 'whitespace-pre-line' : undefined}>
            {renderInline(block.text)}
          </p>
        )
      )}
    </div>
  );
}
