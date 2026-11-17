package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.conversations.ConversationIntent;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tells what a customer's message is about from the words it uses, in Spanish, English, Portuguese and French, without
 * calling the model: tagging every message must cost nothing. It reads the text without accents and in lowercase, so
 * "envío" and "ENVIO" are the same word.
 * <p>
 * It errs on the side of leaving a message untagged: words that mean several things ("cuenta", "open", "cambio") only
 * count inside the phrases where they mean the topic. The team corrects the tags of a conversation by hand.
 */
public final class IntentDetector {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private static final Map<ConversationIntent, Pattern> WORDS = new EnumMap<>(ConversationIntent.class);

    static {
        words(ConversationIntent.ORDER_STATUS,
                "pedidos?", "mi orden", "ordenes", "numero de orden", "rastre\\w*", "seguimiento", "guia",
                "orders?", "tracking", "track my", "commandes?", "suivi", "encomendas?", "rastreio");
        words(ConversationIntent.SHIPPING,
                "envios?", "enviar\\w*", "envian", "envia", "entregas?", "entregar\\w*", "paqueteria", "mensajeria",
                "a domicilio", "cuando llega\\w*", "shipping", "ship", "ships", "delivery", "deliver\\w*",
                "livraison", "livrer", "expedition", "frete", "envio para", "entregam");
        words(ConversationIntent.RETURNS,
                "devolucion\\w*", "devolver", "devuelv\\w*", "reembols\\w*", "garantia", "refunds?", "refunded",
                "returns?", "returning", "exchange", "warranty", "rembours\\w*", "retours?", "garantie",
                "devolucao", "devolver", "troca", "trocar");
        words(ConversationIntent.PAYMENTS,
                "pagos?", "pagar", "pague", "tarjetas?", "facturas?", "facturacion", "cobros?", "cobrar\\w*",
                "me cobraron", "transferencia", "meses sin intereses", "payments?", "pay", "paid", "credit card",
                "invoices?", "billing", "charged", "paiement", "payer", "carte bancaire", "factures?",
                "pagamentos?", "cartao", "boleto", "pix", "nota fiscal");
        words(ConversationIntent.PRICING,
                "precios?", "cuesta", "cuestan", "costos?", "cuanto vale\\w*", "descuentos?", "promociones?",
                "ofertas?", "cupon\\w*", "prices?", "pricing", "how much", "discounts?", "coupons?", "promo\\w*",
                "prix", "coute\\w*", "combien", "remises?", "precos?", "custa", "quanto", "descontos?", "cupom");
        words(ConversationIntent.AVAILABILITY,
                "disponibles?", "disponibilidad", "stock", "existencias?", "agotad\\w*", "tallas?", "en existencia",
                "available", "availability", "in stock", "out of stock", "sold out", "disponibilite",
                "en stock", "rupture", "taille", "disponivel", "estoque", "esgotad\\w*", "tamanho");
        words(ConversationIntent.HOURS_AND_LOCATION,
                "horarios?", "a que hora abren", "abren", "abierto", "cierran", "sucursal\\w*", "tienda fisica",
                "ubicacion", "donde estan", "donde se encuentran", "opening hours", "are you open",
                "store hours", "located", "location", "horaires?", "ouvert\\w*", "adresse", "magasin",
                "funcionamento", "endereco", "loja fisica", "onde fica\\w*");
        words(ConversationIntent.ACCOUNT,
                "mi cuenta", "crear una cuenta", "crear cuenta", "contrasena", "iniciar sesion", "inicio de sesion",
                "mi usuario", "my account", "password", "sign in", "log in", "login", "mon compte",
                "mot de passe", "se connecter", "minha conta", "senha");
        words(ConversationIntent.COMPLAINT,
                "quejas?", "quejarme", "reclamos?", "reclamacion", "inconformidad", "complaint", "complain\\w*",
                "plaintes?", "reclamation", "reclamacao", "reclamar");
    }

    private IntentDetector() {
    }

    /**
     * @return the topics the message is about; empty when none is recognized
     */
    public static Set<ConversationIntent> of(String text) {
        Set<ConversationIntent> intents = EnumSet.noneOf(ConversationIntent.class);
        if (text == null || text.isBlank()) {
            return intents;
        }
        String folded = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
        WORDS.forEach((intent, pattern) -> {
            if (pattern.matcher(folded).find()) {
                intents.add(intent);
            }
        });
        return intents;
    }

    // Whole words or phrases only: "pago" must not match "pagoda", nor "ship" match "relationship"
    private static void words(ConversationIntent intent, String... alternatives) {
        WORDS.put(intent, Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + String.join("|", alternatives)
                + ")(?![\\p{L}\\p{N}])"));
    }
}
