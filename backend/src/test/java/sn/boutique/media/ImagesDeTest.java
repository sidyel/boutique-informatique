package sn.boutique.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.imageio.ImageIO;

/** Fabrique d'images pour les tests : vraies images (ImageIO) et en-têtes fabriqués à la main. */
final class ImagesDeTest {

    private ImagesDeTest() {
    }

    /** Vraie image encodée par ImageIO (« jpg » ou « png »). */
    static byte[] image(String format, int largeur, int hauteur) {
        BufferedImage image = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
        Graphics2D dessin = image.createGraphics();
        dessin.setColor(Color.ORANGE);
        dessin.fillRect(0, 0, largeur / 2, hauteur / 2);
        dessin.dispose();
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, sortie);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sortie.toByteArray();
    }

    /**
     * Bombe de décompression : PNG valide de {@code cote} × {@code cote} pixels en niveaux de gris, tous noirs.
     * Les données compressées tiennent dans quelques dizaines de Ko alors que l'image décodée occupe cote² octets
     * (81 Mo pour 9 000 px) ; elle est produite en flux, sans jamais allouer l'image.
     */
    static byte[] bombePng(int cote) {
        ByteArrayOutputStream compresse = new ByteArrayOutputStream();
        try (DeflaterOutputStream flux = new DeflaterOutputStream(compresse, new Deflater(Deflater.BEST_COMPRESSION))) {
            byte[] ligne = new byte[cote + 1]; // octet de filtre + pixels
            for (int i = 0; i < cote; i++) {
                flux.write(ligne);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(cote).putInt(cote)
                .put((byte) 8).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        morceauPng(png, "IHDR", ihdr.array());
        morceauPng(png, "IDAT", compresse.toByteArray());
        morceauPng(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    /** En-tête JPEG minimal (SOI, SOF0, EOI) annonçant les dimensions données. */
    static byte[] enteteJpeg(int largeur, int hauteur) {
        ByteBuffer jpeg = ByteBuffer.allocate(2 + 2 + 2 + 15 + 2);
        jpeg.put((byte) 0xFF).put((byte) 0xD8);                    // SOI
        jpeg.put((byte) 0xFF).put((byte) 0xC0).putShort((short) 17); // SOF0, longueur
        jpeg.put((byte) 8).putShort((short) hauteur).putShort((short) largeur).put((byte) 3);
        for (int composante = 1; composante <= 3; composante++) {
            jpeg.put((byte) composante).put((byte) 0x11).put((byte) 0);
        }
        jpeg.put((byte) 0xFF).put((byte) 0xD9);                    // EOI
        return jpeg.array();
    }

    /** WebP étendu (en-tête RIFF + morceau VP8X) annonçant les dimensions du canevas. */
    static byte[] enteteWebp(int largeur, int hauteur) {
        ByteBuffer vp8x = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
        vp8x.putInt(0); // drapeaux + réservé
        vp8x.put(sur24Bits(largeur - 1)).put(sur24Bits(hauteur - 1));
        ByteBuffer webp = ByteBuffer.allocate(12 + 8 + 10).order(ByteOrder.LITTLE_ENDIAN);
        webp.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(4 + 8 + 10)
                .put("WEBP".getBytes(StandardCharsets.US_ASCII))
                .put("VP8X".getBytes(StandardCharsets.US_ASCII)).putInt(10).put(vp8x.array());
        return webp.array();
    }

    private static byte[] sur24Bits(int valeur) {
        return new byte[] {(byte) valeur, (byte) (valeur >> 8), (byte) (valeur >> 16)};
    }

    private static void morceauPng(ByteArrayOutputStream png, String type, byte[] donnees) {
        byte[] nom = type.getBytes(StandardCharsets.US_ASCII);
        png.writeBytes(ByteBuffer.allocate(4).putInt(donnees.length).array());
        png.writeBytes(nom);
        png.writeBytes(donnees);
        CRC32 crc = new CRC32();
        crc.update(nom);
        crc.update(donnees);
        png.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
