export function sanitizePromptText(value) {
  return String(value ?? '').replace(/<\|/g, '< |').replace(/<\/s>/g, '</ s>');
}
