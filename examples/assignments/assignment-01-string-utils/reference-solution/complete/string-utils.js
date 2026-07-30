export function truncate(text, maxLength) {
  return Array.from(text).slice(0, maxLength).join('');
}

export function slugify(text) {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .replace(/[^\p{Letter}\p{Number}]+/gu, '-')
    .replace(/^-+|-+$/g, '');
}

export function titleCase(text) {
  return text
    .toLowerCase()
    .replace(/(^|[\s-])(\p{Letter})/gu, (_, boundary, letter) => `${boundary}${letter.toUpperCase()}`);
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
