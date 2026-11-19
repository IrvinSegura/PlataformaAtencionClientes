// Operator-facing texts for why a document or web page could not be processed. The backend stores these reasons
// in English; a reason not listed here is shown as it came, so a new one is never hidden.
const exact: Record<string, string> = {
  "The PDF is password protected": "El PDF está protegido con contraseña.",
  "The PDF contains no selectable text; scanned documents need OCR first":
    "El PDF no tiene texto seleccionable; si es escaneado, pásalo antes por un OCR.",
  "The PDF is damaged or not a PDF": "El PDF está dañado o no es un PDF.",
  "The file is password protected": "El archivo está protegido con contraseña.",
  "The file has too much text; split it into smaller files":
    "El archivo tiene demasiado texto; divídelo en varios.",
  "The spreadsheet has no cells with text":
    "La hoja de cálculo no tiene celdas con texto.",
  "The document has no text": "El documento no tiene texto.",
  "The spreadsheet is damaged or not an Excel file":
    "La hoja de cálculo está dañada o no es un archivo de Excel.",
  "The document is damaged or not a Word file":
    "El documento está dañado o no es un archivo de Word.",
  "The file is not valid UTF-8 text": "El archivo no es texto UTF-8 válido.",
  "The file is not readable text; save it as UTF-8 and upload it again":
    "El archivo no es texto legible; guárdalo como UTF-8 y súbelo de nuevo.",
  "The file looks like binary data, not text":
    "El archivo parece binario, no texto.",
  "The page could not be read": "No se pudo leer la página.",
  "The page has no readable text; it may need JavaScript to show its content":
    "El sitio carga su texto con JavaScript. Guárdala como PDF y súbela.",
  "The page has too much text": "La página tiene demasiado texto.",
  "The page redirects too many times": "La página redirige demasiadas veces.",
  "The page redirects to an invalid address":
    "La página redirige a una dirección no válida.",
  "Only http and https pages can be imported":
    "Solo se pueden importar páginas http y https.",
  "The address has no host": "La dirección no tiene dominio.",
  "Only pages on the standard web ports can be imported":
    "Solo se pueden importar páginas en los puertos web estándar.",
  "Pages on private, local or reserved networks cannot be imported":
    "No se pueden importar páginas de redes privadas, locales o reservadas.",
  "The site took too long to answer": "El sitio tardó demasiado en responder.",
  "The site could not be reached": "No se pudo conectar con el sitio.",
  "The download was interrupted": "La descarga se interrumpió.",
  "The download was cut off": "La descarga quedó incompleta.",
  "The embedding service is unavailable; process the document again later":
    "El servicio de indexación no está disponible; vuelve a procesarlo más tarde.",
  "Unexpected error while processing the document":
    "Error inesperado al procesar el documento.",
  "Unknown error": "Error desconocido.",
};

// Reasons that carry a value, such as a number of pages or an HTTP status
const patterns: [RegExp, (match: RegExpMatchArray) => string][] = [
  [
    /^The PDF has more than (\d+) pages$/,
    (m) => `El PDF tiene más de ${m[1]} páginas.`,
  ],
  [
    /^The page does not exist \(HTTP (\d+)\)$/,
    (m) => `La página no existe (HTTP ${m[1]}).`,
  ],
  [
    /^The site answered HTTP (\d+); check that the page is public$/,
    (m) => `El sitio respondió HTTP ${m[1]}; revisa que la página sea pública.`,
  ],
  [
    /^The address is not a web page \((.*)\); upload files such as PDFs directly$/,
    (m) =>
      `La dirección no es una página web (${m[1]}); sube los archivos, como los PDF, directamente.`,
  ],
  [
    /^The site's name does not exist: (.+)$/,
    (m) => `El dominio no existe: ${m[1]}.`,
  ],
  [
    /^The page is larger than (\d+) MB$/,
    (m) => `La página pesa más de ${m[1]} MB.`,
  ],
];

export function failureReasonText(reason: string): string {
  const text = exact[reason];
  if (text) {
    return text;
  }
  for (const [pattern, translate] of patterns) {
    const match = reason.match(pattern);
    if (match) {
      return translate(match);
    }
  }
  return reason;
}
