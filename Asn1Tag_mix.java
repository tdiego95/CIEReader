package com.example.dturchi.idcardreader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Stream;

enum ASN1TagType {
    TAG_MASK((byte)0x1F),
    BOOLEAN((byte)0x01),
    INTEGER((byte)0x02),
    BIT_STRING((byte)0x03),
    OCTET_STRING((byte)0x04),
    TAG_NULL((byte)0x05),
    OBJECT_IDENTIFIER((byte)0x06),
    OBJECT_DESCRIPTOR((byte)0x07),
    EXTERNAL((byte)0x08),
    REAL((byte)0x09),
    ENUMERATED((byte)0x0a),
    UTF8_STRING((byte)0x0c),
    RELATIVE_OID((byte)0x0d),
    SEQUENCE((byte)0x10),
    SET((byte)0x11),
    NUMERIC_STRING((byte)0x12),
    PRINTABLE_STRING((byte)0x13),
    T61_STRING((byte)0x14),
    VIDEOTEXT_STRING((byte)0x15),
    IA5_STRING((byte)0x16),
    UTC_TIME((byte)0x17),
    GENERALIZED_TIME((byte)0x18),
    GRAPHIC_STRING((byte)0x19),
    VISIBLE_STRING((byte)0x1a),
    GENERAL_STRING((byte)0x1b),
    UNIVERSAL_STRING((byte)0x1C),
    BMPSTRING((byte)0x1E); // 30: Basic Multilingual Plane/Unicode string

    public static final int SIZE = java.lang.Byte.SIZE;

    private byte byteValue;
    private static java.util.HashMap<Byte, ASN1TagType> mappings;
    private static java.util.HashMap<Byte, ASN1TagType> getMappings()
    {
        if (mappings == null)
        {
            synchronized (ASN1TagType.class)
            {
                if (mappings == null)
                {
                    mappings = new java.util.HashMap<Byte, ASN1TagType>();
                }
            }
        }
        return mappings;
    }

    private ASN1TagType(byte value)
    {
        byteValue = value;
        getMappings().put(value, this);
    }

    public byte getValue()
    {
        return byteValue;
    }

    public static ASN1TagType forValue(byte value)
    {
        return getMappings().get(value);
    }
}

/**
 Enumerazione delle classi di tag ASN1
 */
enum ASN1TagClasses {
    CLASS_MASK((byte)0xc0),
    UNIVERSAL((byte)0x00),
    CONSTRUCTED((byte)0x20),
    APPLICATION((byte)0x40),
    CONTEXT_SPECIFIC((byte)0x80),
    PRIVATE((byte)0xc0),
    UNKNOWN((byte)0xff);

    public static final int SIZE = java.lang.Byte.SIZE;

    private byte byteValue;
    private static java.util.HashMap<Byte, ASN1TagClasses> mappings;
    private static java.util.HashMap<Byte, ASN1TagClasses> getMappings()
    {
        if (mappings == null)
        {
            synchronized (ASN1TagClasses.class)
            {
                if (mappings == null)
                {
                    mappings = new java.util.HashMap<Byte, ASN1TagClasses>();
                }
            }
        }
        return mappings;
    }

    private ASN1TagClasses(byte value)
    {
        byteValue = value;
        getMappings().put(value, this);
    }

    public byte getValue()
    {
        return byteValue;
    }

    public static ASN1TagClasses forValue(byte value)
    {
        return getMappings().get(value);
    }
}

class ASN1Tag {
    private byte unusedBits = 0;
    private int startPos, endPos;

    public ASN1Tag(Object[] toArray) {
    }

    private boolean isTagConstructed() {
        return (tag[0] & 0x20) != 0;
    }

    private int tagRawNumber() {
        int num = tag[0];
        for (int i = 1; i < tag.length; i++) {
            num = (int)(num << 8) | tag[i];
        }
        return num;
    }

    /**
     * Ritorna la posizione di inizio del tag all'interno dello stream da cui è stato letto
     */
    public final int getStartPos() {
        return startPos;
    }

    /**
     * Ritorna la posizione di fine del tag all'interno dello stream da cui è stato letto
     */
    public final int getEndPos() {
        return endPos;
    }

    /**
     * L'array di bytes che compone il numero del tag
     */
    public byte[] tag;
    private byte[] data;

    /**
     * Ritorna il contenuto del tag, eventualmente codificando la sequenza di  sotto-tag
     */
    public final byte[] getData() {
        if (data != null) {
            return data;
        } else {

            return null;
            //D return new ByteArrayInputStream(data).read();
            /*try (MemoryStream ms = new MemoryStream()) {
                for (ASN1Tag v : children) {
                    v.Encode(ms);
                }
                return ms.ToArray();
            }*/
        }
    }

    /**
     * La lista dei sotto-tag
     */
    public ArrayList<ASN1Tag> children;

    private static int BytesToInt(byte[] data) {
        int tot = 0;
        for (int i = 0; i < data.length; i++) {
            tot = (tot << 8) | data[i];
        }
        return tot;
    }

    /**
     * Ritorna il numero del tag come intero senza segno e senza la conversione dal formato ASN1
     */
    public final int getTagRawNumber() {
        int num = tag[0];
        for (int i = 1; i < tag.length; i++) {
            num = (int) (num << 8) | tag[i];
        }
        return num;
    }

    /**
     * Ritorna il numero del tag come intero senza segno, secondo le regole di conversione del formato ASN1
     */
    public final int getTagNumber() {
        int num = 0;
        num |= (int) (tag[0] & 0x1f);
        for (int i = 1; i < tag.length; i++) {
            int shift;
            if (i == 1) {
                shift = 5;
            } else {
                shift = 7;
            }
            num = (int) (num << shift) | tag[i];
        }
        return num;
    }

    /**
     * Ritorna true se il numreo del tag include il bit CONSTRUCTED
     */
    public final boolean getTagConstructed() {
        return (tag[0] & 0x20) != 0;
    }

    /**
     Ritorna la classe ricavata dal numero del tag
     */
    public final ASN1TagClasses getTagClass()
    {
        switch (tag[0] & 0xc0)
        {
            case 0x00:
                return ASN1TagClasses.UNIVERSAL;
            case 0x40:
                return ASN1TagClasses.APPLICATION;
            case 0x80:
                return ASN1TagClasses.CONTEXT_SPECIFIC;
            case 0xc0:
                return ASN1TagClasses.PRIVATE;
        };
        return ASN1TagClasses.UNKNOWN;
    }

    private static byte[] IntToBytes(int num)
    {
        if (num <= 0xff)
        {
            return new byte[] {(byte)num};
        }
        if (num <= 0xffff)
        {
            return new byte[] {(byte)(num >>> 8), (byte)(num & 0xff)};
        }
        if (num <= 0xffffff)
        {
            return new byte[] {(byte)(num >>> 16), (byte)((num >>> 8) & 0xff), (byte)(num & 0xff)};
        }
        return new byte[] {(byte)(num >>> 24), (byte)((num >>> 16) & 0xff), (byte)((num >>> 8) & 0xff), (byte)(num & 0xff)};
    }

    private byte[] ASN1Length(int len)
    {
        if (len <= 0x7f)
        {
            return new byte[] {(byte)len};
        }
        if (len <= 0xff)
        {
            return new byte[] {(byte)0x81, (byte)len};
        }
        if (len <= 0xffff)
        {
            return new byte[] {(byte)0x82, (byte)(len>>>8), (byte)(len & 0xff)};
        }
        if (len <= 0xffffff)
        {
            return new byte[] {(byte)0x83, (byte)(len >>> 16), (byte)((len >>> 8) & 0xff), (byte)(len & 0xff)};
        }
        return new byte[] {(byte)0x84, (byte)(len >>> 24), (byte)((len >>> 16) & 0xff), (byte)((len >>> 8) & 0xff), (byte)(len & 0xff)};
    }

    /**
     Verifica che il tag corrisponda a quello specificato (sotto forma di intero senza segno raw) e ritorna l'oggetto stesso per costrutti di tipo fluent

     @param tagCheck Il numero del tag da verificare
     @return Lo stesso oggetto ASN1Tag
     */
    public final ASN1Tag CheckTag(int tagCheck)
    {
        if (getTagNumber() != tagCheck) {
            throw new RuntimeException("Check del tag fallito");
        }
        return this;
    }
    /**
     Verifica che il tag corrisponda a quello specificato (sotto forma di array di bytes) e ritorna l'oggetto stesso per costrutti di tipo fluent

     @param tagCheck Il numero del tag da verificare
     @return Lo stesso oggetto ASN1Tag
     */
    public final ASN1Tag CheckTag(byte[] tagCheck)
    {
        if (!AreEqual(tag, tagCheck))
        {
            throw new RuntimeException("Check del tag fallito");
        }
        return this;
    }

    /**
     Ritorna un sotto-tag dell'oggetto

     @param tagNum Il numero di sequenza del sotto-tag (a partire da 0)
     @return L'oggetto che contiene il sotto-tag
     */
    public final ASN1Tag Child(int tagNum)
    {
        return children.get(tagNum);
    }

    /**
     Ritorna un sotto-tag dell'oggetto verificando che il suo numero di tag corrisponda a quello specificato (sotto forma di intero senza segno raw)

     @param tagNum Il numero di sequenza  del sotto-tag (a partire da 0)
     @param tagCheck Il numero del sotto-tag da verificare
     @return L'oggetto che contiene il sotto-tag
     */
    public final ASN1Tag Child(int tagNum, int tagCheck)
    {
        ASN1Tag tag = children.get(tagNum);
        if (getTagRawNumber() != tagCheck)
        {
            throw new RuntimeException("Check del tag fallito");
        }
        return tag;
    }
    /**
     Ritorna un sotto-tag che abbia il numero di tag (sotto forma di intero senza segno raw) corrispondente a quello specificato

     @param tagId Il numero del tag da cercare
     @return L'oggetto che contiene il sotto-tag, o null se non viene trovato
     */
    public final ASN1Tag ChildWithTagId(int tagId)
    {
//C# TO JAVA CONVERTER TODO TASK: There is no equivalent to implicit typing in Java unless the Java 10 inferred typing option is selected:
        for (ASN1Tag tag : children)
        {
            if (/*D tag.tagRawNumber*/ 0 == tagId)
            {
                return tag;
            }
        }
        return null;
    }

    /// <summary>
    /// Ritorna un sotto-tag che abbia il numero di tag (sotto forma di array di bytes) corrispondente a quello specificato
    /// </summary>
    /// <param name="tagId">Il numero del tag da cercare</param>
    /// <returns>L'oggetto che contiene il sotto-tag, o null se non viene trovato</returns>
    public ASN1Tag ChildWithTagId(byte[] tagId)
    {
        /*D foreach (var tag in children)
        {
            if (AreEqual(tag.tag,tagId))
                return tag;
        }*/
        return null;
    }

    public boolean AreEqual(byte[] a, byte[] b)
    {
        if (a.length != b.length)
            return false;
        for (int i = 0; i < a.length; i++)
            if (a[i] != b[i])
                return false;

        return true;
    }

    /// <summary>
    /// Verifica che il contenuto del tag corrisponda all'array di bytes specficato. Solleva un'eccezione se non corrispondono
    /// </summary>
    /// <param name="dataCheck">L'array di bytes da verificare</param>
    public void Verify(byte[] dataCheck) throws Exception {
        if (!AreEqual(data, dataCheck))
            throw new Exception("Check del contenuto fallito");
    }

    /// <summary>
    /// Ritorna un sotto-tag dell'oggetto verificando che il suo numero di tag corrisponda a quello specificato (sotto forma di intero senza segno raw)
    /// </summary>
    /// <param name="tagNum">Il numero di sequenza  del sotto-tag (a partire da 0)</param>
    /// <param name="tagCheck">Il numero del sotto-tag da verificare</param>
    /// <returns>L'oggetto che contiene il sotto-tag</returns>
    public ASN1Tag Child(int tagNum, byte[] tagCheck) throws Exception {
        ASN1Tag subTag = children.get(tagNum);
        if (!AreEqual(subTag.tag, tagCheck))
            throw new Exception("Check del tag fallito");
        /*if (tag.tagRawNumber != tagCheck.toInt())
            throw Asn1TagParseException("Check del tag fallito")*/
        return subTag;
    }

    /// <summary>
    /// Crea un tag con l'identificativo e il contenuto specificato
    /// </summary>
    /// <param name="tag">Il numero del tag sotto forma di intero senza segno raw</param>
    /// <param name="data">L'array di bytes che contiene i dati del tag</param>
    public ASN1Tag(int tag, byte[] data)
    {
        this.tag = IntToBytes(tag);
        this.data = data;
        this.children = null;
    }

    /// <summary>
    /// Crea un tag con l'identificativo e il contenuto specificato
    /// </summary>
    /// <param name="tag">Il numero del tag sotto forma di array di bytes</param>
    /// <param name="data">L'array di bytes che contiene i dati del tag</param>
    public ASN1Tag(byte[] tag, byte[] data)
    {
        this.tag = tag;
        this.data = data;
        this.children = null;
    }

    /// <summary>
    /// Crea un tag vuoto con l'identificativo specificato
    /// </summary>
    /// <param name="tag">Il numero del tag sotto forma di array di bytes</param>
    public ASN1Tag(byte[] tag)
    {
        this.tag = tag;
        this.data = null;
        this.children=null;
    }

    /// <summary>
    /// Crea un tag con l'identificativo e il contenuto specificato
    /// </summary>
    /// <param name="tag">Il numero del tag sotto forma di array di bytes</param>
    /// <param name="children">L'elenco di tag che contiene i sotto-tag dell'oggetto</param>
    /*D public ASN1Tag(byte[] tag, IEnumerable<ASN1Tag> children)
    {
        this.tag = tag;
        this.data = null;
        this.children = new List<ASN1Tag>();
        this.children.AddRange(children);
    }*/

    /**
     Crea un tag con l'identificativo e il contenuto specificato

     @param tag Il numero del tag sotto forma di intero senza segno raw
     @param children L'elenco di tag che contiene i sotto-tag dell'oggetto
     */
    public ASN1Tag(int tag, java.lang.Iterable<ASN1Tag> children)
    {
        this.tag = IntToBytes(tag);
        this.data = null;
        this.children = new ArrayList<ASN1Tag>();
        for(ASN1Tag tagg : children)
            this.children.add(tagg);
        //this.children.AddRange(children);
    }

    /**
     Legge dallo stream una lunghezza codificata in ASN1 e la restituisce

     @param s Lo stream da leggere
     @return
     */
//C# TO JAVA CONVERTER TODO TASK: C# to Java Converter cannot determine whether this System.IO.Stream is input or output:
    public static int ParseLength(Stream s)
    {
        int size = 0;
        /*D tangible.RefObject<Integer> tempRef_size = new tangible.RefObject<Integer>(size);
        int tempVar = ParseLength(s, 0, (int)s.Length, tempRef_size);
        size = tempRef_size.argValue;
        return tempVar;*/
        return size;
    }

    /**
     Legge dall'array di bytes una lunghezza codificata in ASN1 e la restituisce

     @param data L'array di bytes che contiene la lunghezza
     @return
     */
    public static int ParseLength(byte[] data)
    {
        /*D try (MemoryStream ms = new MemoryStream(data))
        {
            return ParseLength(ms);
        }*/
        return 0;
    }

    /**
     Decodifica il contenuto di un array di bytes in una struttura ASN1

     @param data I dati da decodificare
     @param reparse true per applicare il parse ai tag che hanno contenuto binario ed eventualmente rappresentarli cone struttura ASN1
     @return L'oggetto ASN1Tag di livello più alto della struttura
     */
    public static ASN1Tag Parse(byte[] data, boolean reparse)
    {
        ByteArrayInputStream ms = new ByteArrayInputStream(data);
        return Parse(ms, 0, data.length, reparse);
    }

    /**
     Decodifica il contenuto di un array di bytes in una struttura ASN1. Applica il parse ai tag che hanno contenuto binario ed eventualmente li rappresenta cone struttura ASN1

     @param data I dati da decodificare
     @return L'oggetto ASN1Tag di livello più alto della struttura
     */
    public static ASN1Tag Parse(byte[] data)
    {
        /*D try (MemoryStream ms = new MemoryStream(data))
        {
            return Parse(ms,true);
        }*/
        return null;
    }

    /**
     Decodifica il contenuto di uno stream in una struttura ASN1. Applica il parse ai tag che hanno contenuto binario ed eventualmente li rappresenta cone struttura ASN1

     @param s Lo stream da decodificare
     @return L'oggetto ASN1Tag di livello più alto della struttura
     */
//C# TO JAVA CONVERTER TODO TASK: C# to Java Converter cannot determine whether this System.IO.Stream is input or output:
    public static ASN1Tag Parse(ByteArrayInputStream s)
    {
        //D return Parse(s, true);
        return null;
    }

    public static ASN1Tag Parse(ByteArrayInputStream s, int start, int length, /*D RefObject<Integer> size,*/ boolean reparse)
    {
        int readPos = 0;
        if (readPos == length) {
            throw new RuntimeException();
        }
        // leggo il tag
        ArrayList<Byte> tagVal = new ArrayList<Byte>();
        int tag = s.read();
        readPos++;
        tagVal.add((byte)tag);
        if ((tag & 0x1f) == 0x1f) {
            // è un tag a più bytes; proseguo finchè non trovo un bit 8 a 0
            while (true) {
                if (readPos == length) {
                    throw new RuntimeException();
                }
                tag = s.read();
                readPos++;
                tagVal.add((byte)tag);
                if ((tag & 0x80) != 0x80)
                {
                    // è l'ultimo byte del tag
                    break;
                }
            }
        }
        // leggo la lunghezza
        if (readPos == length) {
            throw new RuntimeException();
        }
        int len = s.read();
        readPos++;
        if (len > 0x80) {
            int lenlen = len - 0x80;
            len = 0;
            for (int i = 0; i < lenlen; i++) {
                if (readPos == length) {
                    throw new RuntimeException();
                }
                len = ((len << 8) | (byte)s.read());
                readPos++;
            }
        }
        else if (len == 0x80)
        {
            throw new RuntimeException("Lunghezza indefinita non supportata");
        }
        int size = readPos + len;
        if (size < length) { //D era > .. ma non funzia
            throw new RuntimeException("ASN1 non valido");
        }
        if (tagVal.size() == 1 && tagVal.get(0).equals(0) && len == 0) {
            return null;
        }
        byte[] data = new byte[len];
        s.read(data, 0, len);
        ByteArrayInputStream ms = new ByteArrayInputStream(data);
        // quando devo parsare i sotto tag??
        // in teoria solo se il tag è constructed, ma
        // spesso una octetstring o una bitstring sono
        // delle strutture ASN1...
        ASN1Tag newTag = new ASN1Tag(tagVal.toArray());
        ArrayList<ASN1Tag> children = null;
        int parsedLen = 0;
        boolean parseSubTags = false;
        if (newTag.isTagConstructed())
        {
            parseSubTags = true;
        }
        else if (reparse && KnownTag(newTag.tag).equals("OCTET STRING"))
        {
            parseSubTags = true;
        }
        else if (reparse && KnownTag(newTag.tag).equals("BIT STRING"))
        {
            parseSubTags = true;
            newTag.unusedBits = (byte)ms.read();
            parsedLen++;
        }

        if (parseSubTags) {
            children = new ArrayList<ASN1Tag>();
            while (true) {
                int childSize = 0;
                try {
                    ASN1Tag child = Parse(ms, start + readPos + parsedLen, (int)(len - parsedLen), reparse);
                    if (child != null) {
                        children.add(child);
                    }
                } catch (java.lang.Exception e) {
                    children = null;
                    break;
                }
                parsedLen += childSize;
                if (parsedLen > len) {
                    children = null;
                    break;
                } else if (parsedLen == len) {
                    data = null;
                    break;
                }
            }
        }
        newTag.startPos = start;
        newTag.endPos = start + size;
        if (children == null) {
            newTag.data = data;
        } else {
            newTag.children = children;
        }
        return newTag;
    }

    /**
     Codifica il tag contenuto nell'oggetto in un uno stream

     @param s Lo stream in cui codificare l'oggetto
     */
    public final void Encode(OutputStream s)
    {
        /*D ArrayList<byte[]> childs = null;
        s.write(tag, 0, tag.Length);
        int len = 0;
        if (data != null)
        {
            len = (int)data.Length;
        }
        else
        {
            childs = new ArrayList<byte[]>();
            len = 0;
            for (ASN1Tag t : children)
            {
                try (MemoryStream ms = new MemoryStream())
                {
                    t.Encode(ms);
                    byte[] dat = ms.ToArray();
                    len += (int)dat.length;
                    childs.add(dat);
                }
            }

        }
        if (tag[0] == 3 && tag.Length == 1 && data == null)
        {
            len++;
        }
        if (len < 128)
        {
            s.write((byte)len);
        }
        else if (len < 256)
        {
            s.write(0x81);
            s.write((byte)len);
        }
        else if (len <= 0xffff)
        {
            s.write(0x82);
            s.write((byte)(len >>> 8));
            s.write((byte)(len & 0xff));
        }
        else if (len <= 0xffffff)
        {
            s.write(0x83);
            s.write((byte)(len >>> 16));
            s.write((byte)((len >>> 8) & 0xff));
            s.write((byte)(len & 0xff));
        }
        else
        {
            s.write(0x84);
            s.write((byte)(len >>> 24));
            s.write((byte)((len >>> 16) & 0xff));
            s.write((byte)((len >>> 8) & 0xff));
            s.write((byte)(len & 0xff));
        }
        if (tag[0] == 3 && tag.Length == 1 && data == null)
        {
            s.write(unusedBits);
        }

        if (data != null)
        {
            s.write(data, 0, data.Length);
        }
        else
        {
            for (byte[] t : childs)
            {
                s.write(t, 0, t.length);
            }
        }*/
    }

    /*D public static int ParseLength(InputStream s, int start, int length, tangible.RefObject<Integer> size)
    {
        int readPos = 0;
        if (readPos == length)
        {
            throw new RuntimeException();
        }
        // leggo il tag
        ArrayList<Byte> tagVal = new ArrayList<Byte>();
        int tag = s.read();
        readPos++;
        tagVal.add((byte)tag);
        if ((tag & 0x1f) == 0x1f)
        {
            // è un tag a più bytes; proseguo finchè non trovo un bit 8 a 0
            while (true)
            {
                if (readPos == length)
                {
                    throw new RuntimeException();
                }
                tag = s.read();
                readPos++;
                tagVal.add((byte)tag);
                if ((tag & 0x80) != 0x80)
                {
                    // è l'ultimo byte del tag
                    break;
                }
            }
        }
        // leggo la lunghezza
        if (readPos == length)
        {
            throw new RuntimeException();
        }
        int len = (int)s.read();
        readPos++;
        if (len > 0x80)
        {
            int lenlen = len - 0x80;
            len = 0;
            for (int i = 0; i < lenlen; i++)
            {
                if (readPos == length)
                {
                    throw new RuntimeException();
                }
                len = (int)((len << 8) | (byte)s.read());
                readPos++;
            }
        }
        size.argValue = (int)(readPos + len);
        return size.argValue.intValue();

    }

    //C# TO JAVA CONVERTER TODO TASK: C# to Java Converter cannot determine whether this System.IO.Stream is input or output:
    public static ASN1Tag Parse(Stream s, int start, int length, tangible.RefObject<Integer> size)
    {
        return Parse(s, start, length, size, true);
    }

    @Override
    public String toString()
    {
        String val = KnownTag(tag);
        if (val == null)
        {
            val = tagClass.toString() + " " + String.format("%1$.2X", tagNumber);
        }
        if (tagConstructed)
        {
            val += " Constructed ";
        }
        val += " (" + (new ByteArray(tag)).toString() + ") ";
        if (display == null)
        {
            val += ASN1GenericDisplay.singleton.contentString(this);
        }
        else
        {
            val += display.contentString(this);
        }
        return val;
    }

    private static IASN1Display KnownDisplay(byte[] tag)
    {
        if (tag.length == 1)
        {
            switch (tag[0])
            {
                case 5:
                    return ASN1NullDisplay.singleton;
                case 6:
                    return ASN1ObjIdDisplay.singleton;
                case 12:
                    return ASN1StringDisplay.singleton;
                case 19:
                    return ASN1StringDisplay.singleton;
                case 20:
                    return ASN1StringDisplay.singleton;
                case 22:
                    return ASN1StringDisplay.singleton;
                case 23:
                    return ASN1UTCTimeDisplay.singleton;
            }
        }
        return null;
    }*/

    private static String KnownTag(byte[] tag)
    {
        if (tag.length == 1)
        {
            switch (tag[0])
            {
                case 2:
                    return "INTEGER";
                case 3:
                    return "BIT STRING";
                case 4:
                    return "OCTET STRING";
                case 5:
                    return "NULL";
                case 6:
                    return "OBJECT IDENTIFIER";
                case 0x30:
                    return "SEQUENCE";
                case 0x31:
                    return "SET";
                case 12:
                    return "UTF8 String";
                case 19:
                    return "PrintableString";
                case 20:
                    return "T61String";
                case 22:
                    return "IA5String";
                case 23:
                    return "UTCTime";
            }
        }
        return null;
    }

}
