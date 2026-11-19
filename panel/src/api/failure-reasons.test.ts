import { describe, expect, it } from "vitest";
import { failureReasonText } from "./failure-reasons";

describe("failureReasonText", () => {
  it("translates a fixed reason", () => {
    expect(
      failureReasonText(
        "The page has no readable text; it may need JavaScript to show its content",
      ),
    ).toBe(
      "El sitio carga su texto con JavaScript. Guárdala como PDF y súbela.",
    );
    expect(failureReasonText("The PDF is password protected")).toBe(
      "El PDF está protegido con contraseña.",
    );
  });

  it("keeps the values of a reason that carries them", () => {
    expect(failureReasonText("The page does not exist (HTTP 404)")).toBe(
      "La página no existe (HTTP 404).",
    );
    expect(
      failureReasonText(
        "The site answered HTTP 403; check that the page is public",
      ),
    ).toBe("El sitio respondió HTTP 403; revisa que la página sea pública.");
    expect(
      failureReasonText("The site's name does not exist: tienda.example"),
    ).toBe("El dominio no existe: tienda.example.");
    expect(failureReasonText("The PDF has more than 500 pages")).toBe(
      "El PDF tiene más de 500 páginas.",
    );
    expect(
      failureReasonText(
        "The address is not a web page (application/pdf); upload files such as PDFs directly",
      ),
    ).toBe(
      "La dirección no es una página web (application/pdf); sube los archivos, como los PDF, directamente.",
    );
  });

  it("shows a reason it does not know as it came", () => {
    expect(failureReasonText("Something new happened")).toBe(
      "Something new happened",
    );
  });
});
