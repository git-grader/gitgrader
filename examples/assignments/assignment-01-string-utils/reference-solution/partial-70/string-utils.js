export function truncate(text, maxLength) {
  return text.slice(0, maxLength);
}

export function slugify(text) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

export function titleCase(text) {
  return text
    .toLowerCase()
    .replace(/(^|\s)([a-z])/g, (_, boundary, letter) => `${boundary}${letter.toUpperCase()}`);
}

export function wordCount(text) {
  const words = text.trim().match(/\S+/gu);
  return words === null ? 0 : words.length;
}

export function reverseWords(text) {
  return text.trim().split(/\s+/u).filter(Boolean).reverse().join(' ');
}

export function isPalindrome(text) {
  const normalised = Array.from(text.toLocaleLowerCase()).filter((character) => /[\p{Letter}\p{Number}]/u.test(character));
  return normalised.join('') === normalised.reverse().join('');
}
