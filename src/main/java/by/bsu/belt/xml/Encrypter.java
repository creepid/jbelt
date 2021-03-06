/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package by.bsu.belt.xml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import java.io.OutputStream;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;

import org.apache.xml.security.algorithms.JCEMapper;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.encryption.EncryptedData;
import org.apache.xml.security.encryption.EncryptedKey;
import org.apache.xml.security.utils.Constants;

import org.apache.xml.security.utils.EncryptionConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * This sample demonstrates how to encrypt data inside an xml document.
 *
 * @author Vishal Mahajan (Sun Microsystems)
 */
public class Encrypter {

    /** {@link org.apache.commons.logging} logging facility */
    static org.apache.commons.logging.Log log = 
        org.apache.commons.logging.LogFactory.getLog(
            Encrypter.class.getName());

    static {
        org.apache.xml.security.Init.init();

    }

    static {
        JCEMapper.register(Identifiers.KEYTRANSPORT_ALGO_URI, new JCEMapper.Algorithm("", "Bign", "KeyTransport"));
        JCEMapper.register(Identifiers.ENCRYPTION_URI, new JCEMapper.Algorithm("", "Belt", "BlockEncryption"));
    }

    private static Document createSampleDocument() throws Exception {

        javax.xml.parsers.DocumentBuilderFactory dbf =
            javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.newDocument();

        /**
         * Build a sample document. It will look something like:
         *
         * <apache:RootElement xmlns:apache="http://www.apache.org/ns/#app1">
         * <apache:foo>Some simple text</apache:foo>
         * </apache:RootElement>
         */
        Element root =
            document.createElementNS("http://www.apache.org/ns/#app1", "apache:RootElement");
        root.setAttributeNS(
            Constants.NamespaceSpecNS, "xmlns:apache", "http://www.apache.org/ns/#app1"
        );
        document.appendChild(root);

        root.appendChild(document.createTextNode("\n"));

        Element childElement =
            document.createElementNS("http://www.apache.org/ns/#app1", "apache:foo");
        childElement.appendChild(
            document.createTextNode("Some simple text"));
        root.appendChild(childElement);

        root.appendChild(document.createTextNode("\n"));

        return document;
    }

    public SecretKey Kek = null;

    private static SecretKey GenerateAndStoreKeyEncryptionKey() throws Exception {
        String jceAlgorithmName = "DESede";
        KeyGenerator keyGenerator =
            KeyGenerator.getInstance(jceAlgorithmName);
        SecretKey kek = keyGenerator.generateKey();

        byte[] keyBytes = kek.getEncoded();
        File kekFile = new File("build/kek");
        kekFile.createNewFile();
        FileOutputStream f = new FileOutputStream(kekFile);
        f.write(keyBytes);
        f.close();
        System.out.println("Key encryption key stored in " + kekFile.toURI().toURL().toString());

        return kek;
    }

    private static SecretKey GenerateDataEncryptionKey() throws Exception {
        KeyGenerator keyGenerator =
            KeyGenerator.getInstance("Belt");
        //keyGenerator.init(128);
        return keyGenerator.generateKey();

    }

    public static String transform(Document doc) throws TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        DOMSource source = new DOMSource(doc);
        OutputStream stream = new ByteArrayOutputStream();
        StreamResult result = new StreamResult(stream);
        transformer.transform(source, result);
        return result.getOutputStream().toString();
    }

    private static void outputDocToFile(Document doc, String fileName) throws Exception {
        File encryptionFile = new File(fileName);
        FileOutputStream f = new FileOutputStream(encryptionFile);

        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(f);
        transformer.transform(source, result);

        f.close();
        System.out.println(
            "Wrote document containing encrypted data to " + encryptionFile.toURI().toURL().toString()
        );
    }

    public static Document encrypt(PublicKey pubKey, Document document) throws Exception {

        if (document==null)
            document = createSampleDocument();

        /*
         * Get a key to be used for encrypting the element.
         * Here we are generating an Belt key.
         */
        Key symmetricKey = GenerateDataEncryptionKey();

        /*
         * Get a key to be used for encrypting the symmetric key.
         * Here we are generating a Bign key.
         */
        XMLCipher keyCipher =
            XMLCipher.getInstance(Identifiers.KEYTRANSPORT_ALGO_URI);
        keyCipher.init(XMLCipher.WRAP_MODE, pubKey);
        EncryptedKey encryptedKey =
            keyCipher.encryptKey(document, symmetricKey);

        /*
         * Let us encrypt the contents of the document element.
         */
        Element rootElement = document.getDocumentElement();

        XMLCipher xmlCipher =
            XMLCipher.getInstance(Identifiers.ENCRYPTION_URI);
        xmlCipher.init(XMLCipher.ENCRYPT_MODE, symmetricKey);

        /*
         * Setting keyinfo inside the encrypted data being prepared.
         */
        EncryptedData encryptedData = xmlCipher.getEncryptedData();
        KeyInfo keyInfo = new KeyInfo(document);
        keyInfo.add(encryptedKey);
        encryptedData.setKeyInfo(keyInfo);

        /*
         * doFinal -
         * "true" below indicates that we want to encrypt element's content
         * and not the element itself. Also, the doFinal method would
         * modify the document by replacing the EncrypteData element
         * for the data to be encrypted.
         */
        xmlCipher.doFinal(document, rootElement, true);

        /*
         * Output the document containing the encrypted information into
         * a file.
         */
        //outputDocToFile(document, "build/encryptedInfo.xml");
        return document;
    }

    public static Document decrypt(PrivateKey kek, Document document) throws Exception {

        Element encryptedDataElement =
                (Element) document.getElementsByTagNameNS(
                        EncryptionConstants.EncryptionSpecNS,
                        EncryptionConstants._TAG_ENCRYPTEDDATA).item(0);

        /*
         * Load the key to be used for decrypting the xml data
         * encryption key.
         */
        XMLCipher xmlCipher =
                XMLCipher.getInstance();
        /*
         * The key to be used for decrypting xml data would be obtained
         * from the keyinfo of the EncrypteData using the kek.
         */
        xmlCipher.init(XMLCipher.DECRYPT_MODE, null);
        xmlCipher.setKEK(kek);
        /*
         * The following doFinal call replaces the encrypted data with
         * decrypted contents in the document.
         */
        xmlCipher.doFinal(document, encryptedDataElement);

        //outputDocToFile(document, "build/decryptedInfo.xml");
        return document;
    }
}
