/*
 * Copyright (c) 2005-2010 Flamingo Kirill Grouchnikov. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *  o Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer. 
 *     
 *  o Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution. 
 *     
 *  o Neither the name of Flamingo Kirill Grouchnikov nor the names of 
 *    its contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *     
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */

package org.pushingpixels.flamingo.api.svg;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.CompositeGraphicsNode;
import org.apache.batik.gvt.CompositeShapePainter;
import org.apache.batik.gvt.FillShapePainter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.ShapeNode;
import org.apache.batik.gvt.ShapePainter;
import org.apache.batik.gvt.StrokeShapePainter;
import org.apache.batik.gvt.TextNode;
import org.pushingpixels.flamingo.api.svg.transcoders.AffineTransformTranscoder;
import org.pushingpixels.flamingo.api.svg.transcoders.BasicStrokeTranscoder;
import org.pushingpixels.flamingo.api.svg.transcoders.FloatTranscoder;
import org.pushingpixels.flamingo.api.svg.transcoders.PaintTranscoder;
import org.pushingpixels.flamingo.api.svg.transcoders.ShapeTranscoder;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * SVG to Java2D transcoder.
 *
 * @author Kirill Grouchnikov.
 */
public class SvgTranscoder {

    /** The output writer receiving the generated class. */
    protected PrintWriter externalPrintWriter;

    /** Temporary buffer holding the code being generated. */
    protected PrintWriter printWriter;

    /** Class name for the generated Java2D code. */
    protected String javaClassName;

    /** Package name for the generated Java2D code. */
    protected String javaPackageName;

    /** The template of the generated classes */
    private Template template = Template.getDefault();

    /** URL of the SVG image. */
    private URL url;

    /** The current composite. */
    private AlphaComposite currentComposite;

    /** The current paint, as a Java declaration. */
    private String currentPaint;
    
    /** The current stroke, as a Java declaration. */
    private String currentStroke;

    /** The current shape. */
    private Shape currentShape;
    
    /**
     * Creates a new transcoder.
     *
     * @param url           URL of the SVG image.
     * @param javaClassname Classname for the generated Java2D code.
     */
    public SvgTranscoder(URL url, String javaClassname) {
        this(javaClassname);
        this.url = url;
    }

    /**
     * Creates a new transcoder.
     *
     * @param javaClassname Classname for the generated Java2D code.
     */
    public SvgTranscoder(String javaClassname) {
        this.javaClassName = javaClassname;
    }

    /**
     * Returns the filtered image content. The metadata are removed from
     * the document to prevent illegal elements from breaking the parsing.
     * (For example several KDE icons have unrecognized RDF elements)
     */
    private InputStream getInputStream() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser parser = factory.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        reader.setEntityResolver(new EntityResolver() {
            public InputSource resolveEntity(String pid, String sid) throws SAXException {
                return new InputSource(new StringReader(""));
            }
        });
        
        InputSource input;
        if (url.toString().endsWith(".svgz")) {
            input = new InputSource(new GZIPInputStream(url.openStream()));
        } else {
            input = new InputSource(url.openStream());
        }
        SAXSource source = new SAXSource(reader, input);
        
        Result result = new StreamResult(buffer);
        
        StreamSource stylesheet = new StreamSource(getClass().getResourceAsStream("/svg-cleanup.xsl"));
        
        Transformer transformer = TransformerFactory.newInstance().newTransformer(stylesheet);
        transformer.transform(source, result);
        
        stylesheet.getInputStream().close();
        
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    /**
     * Transcodes the SVG image into Java2D code.
     */
    public void transcode() {
        if (externalPrintWriter == null) {
            return;
        }

        UserAgentAdapter ua = new UserAgentAdapter();
        DocumentLoader loader = new DocumentLoader(ua);
        BridgeContext context = new BridgeContext(ua, loader);
        context.setDynamicState(BridgeContext.DYNAMIC);
        ua.setBridgeContext(context);
        
        try {
            Document svgDoc = loader.loadDocument(url.toString(), getInputStream());
            new GVTBuilder().build(context, svgDoc);
            
            transcode(context);
        } catch (Exception e) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Unable to transcode " + url, e);
        }
    }

    public void setTemplate(Template template) {
        this.template = template;
    }

    public void setJavaPackageName(String javaPackageName) {
        this.javaPackageName = javaPackageName;
    }

    public void setPrintWriter(PrintWriter printWriter) {
        this.externalPrintWriter = printWriter;
    }

    /**
     * Transcodes the SVG image into Java2D code.
     */
    public void transcode(BridgeContext context) throws IOException {
        GraphicsNode root = context.getGraphicsNode(context.getDocument());
        
        ByteArrayOutputStream paintingCodeStream = new ByteArrayOutputStream();
        this.printWriter = new IndentingPrintWriter(new PrintWriter(paintingCodeStream));
        transcodeGraphicsNode(root, "");
        this.printWriter.close();
        
        String separator = 
                  "        paint${count}(g, origAlpha, transformations);\n"
                + "    }\n\n"
                + "    private static void paint${count}(Graphics2D g, float origAlpha, java.util.LinkedList<AffineTransform> transformations) {\n"
                + "        Shape shape = null;\n";
        
        String paintingCode = new String(paintingCodeStream.toByteArray());
        paintingCode = TextSplitter.insert(paintingCode, separator, 3000);
        
        Rectangle2D bounds = root.getBounds();
        if (bounds == null) {
            bounds = new Rectangle2D.Double(0, 0, context.getDocumentSize().getWidth(), context.getDocumentSize().getHeight());
        }
        
        Map<Template.Token, Object> params = new HashMap<Template.Token, Object>();
        params.put(Template.Token.PACKAGE, javaPackageName != null ? "package " + javaPackageName + ";" : "");
        params.put(Template.Token.CLASSNAME, javaClassName);
        params.put(Template.Token.X, (int) Math.ceil(bounds.getX()));
        params.put(Template.Token.Y, (int) Math.ceil(bounds.getY()));
        params.put(Template.Token.WIDTH,  (int) Math.ceil(bounds.getWidth()));
        params.put(Template.Token.HEIGHT, (int) Math.ceil(bounds.getHeight()));
        params.put(Template.Token.PAINTING_CODE, paintingCode);

        template.apply(externalPrintWriter, params);
    }

    /**
     * Transcodes the specified shape.
     *
     * @param shape Shape.
     * @throws UnsupportedOperationException if the shape is unsupported.
     */
    private void transcodeShape(Shape shape) throws UnsupportedOperationException {
        if (shape == currentShape) {
            return;
        }
        
        ShapeTranscoder.INSTANCE.transcode(shape, printWriter);
        
        currentShape = shape;
    }

    /**
     * Transcodes the specified shape painter.
     *
     * @param painter Shape painter.
     * @throws UnsupportedOperationException if the shape painter is unsupported.
     */
    private void transcodeShapePainter(ShapePainter painter) throws UnsupportedOperationException {
        if (painter instanceof CompositeShapePainter) {
            transcodeCompositeShapePainter((CompositeShapePainter) painter);
        } else if (painter instanceof FillShapePainter) {
            transcodeFillShapePainter((FillShapePainter) painter);
        } else if (painter instanceof StrokeShapePainter) {
            transcodeStrokeShapePainter((StrokeShapePainter) painter);
        } else if (painter != null) {
            throw new UnsupportedOperationException(painter.getClass().getCanonicalName());
        }
    }

    /**
     * Transcodes the specified composite shape painter.
     *
     * @param painter Composite shape painter.
     */
    private void transcodeCompositeShapePainter(CompositeShapePainter painter) {
        for (int i = 0; i < painter.getShapePainterCount(); i++) {
            transcodeShapePainter(painter.getShapePainter(i));
        }
    }

    /**
     * Transcodes the specified fill shape painter.
     *
     * @param painter Fill shape painter.
     */
    private void transcodeFillShapePainter(FillShapePainter painter) {
        Paint paint = (Paint) painter.getPaint();
        if (paint == null) {
            return;
        }
        
        transcodeShape(painter.getShape());
        transcodePaintChange(paint);
        printWriter.println("g.fill(shape);");
    }

    private void transcodePaintChange(Paint paint) {
        String p = PaintTranscoder.INSTANCE.transcode(paint);
        if (!p.equals(currentPaint)) {
            currentPaint = p;
            printWriter.println("g.setPaint(" + currentPaint + ");");
        }
    }

    /**
     * Transcodes the specified stroke shape painter.
     *
     * @param painter Stroke shape painter.
     */
    private void transcodeStrokeShapePainter(StrokeShapePainter painter) {
        Paint paint = (Paint) painter.getPaint();
        if (paint == null) {
            return;
        }
        
        transcodeShape(painter.getShape());
        transcodePaintChange(paint);
        transcodeStrokeChange(painter.getStroke());
        printWriter.println("g.draw(shape);");
    }

    private void transcodeStrokeChange(Stroke stroke) {
        String s = BasicStrokeTranscoder.INSTANCE.transcode((BasicStroke) stroke);
        if (s == null && currentStroke != null || s != null && !s.equals(currentStroke)) {
            currentStroke = s;
            printWriter.println("g.setStroke(" + s + ");");
        }
    }

    private void transcodeCompositeChange(AlphaComposite composite) {
        if (composite != null && !composite.equals(currentComposite) && !(currentComposite == null && composite.getAlpha() == 1)) {
            currentComposite = composite;
            printWriter.println("g.setComposite(AlphaComposite.getInstance(" + composite.getRule() + ", " + FloatTranscoder.INSTANCE.transcode(composite.getAlpha()) + " * origAlpha));");
        }
    }

    /**
     * Transcodes the specified graphics node.
     *
     * @param node    Graphics node.
     * @param comment Comment (for associating the Java2D section with the corresponding SVG section).
     * @throws UnsupportedOperationException if the graphics node is unsupported.
     */
    private void transcodeGraphicsNode(GraphicsNode node, String comment) throws UnsupportedOperationException {
        transcodeCompositeChange((AlphaComposite) node.getComposite());
        
        AffineTransform transform = node.getTransform();
        if (transform != null && !transform.isIdentity()) {
            printWriter.println("transformations.offer(g.getTransform());");
            printWriter.println("g.transform(" + AffineTransformTranscoder.INSTANCE.transcode(transform) + ");");
        }
        
        try {
            printWriter.println("");
            printWriter.println("// " + comment);
            if (node instanceof ShapeNode) {
                transcodeShapePainter(((ShapeNode) node).getShapePainter());
            } else if (node instanceof CompositeGraphicsNode) {
                List children = ((CompositeGraphicsNode) node).getChildren();
                for (int i = 0; i < children.size(); i++) {
                    transcodeGraphicsNode((GraphicsNode) children.get(i), comment + "_" + i);
                }
            } else if (node instanceof TextNode) {
                transcodeTextNode((TextNode) node);
            } else {
                throw new UnsupportedOperationException(node.getClass().getCanonicalName());
            }
        } finally {
            if (transform != null && !transform.isIdentity()) {
                printWriter.println("");
                printWriter.println("g.setTransform(transformations.pollLast()); // " + comment);
            }
        }
    }

    /**
     * Transcode the specified text node.
     * 
     * @param text
     */
    private void transcodeTextNode(TextNode text) {
        if (text.getText() == null) {
            return;
        }
        
        printWriter.println("// " + text.getText().replaceAll("[\\r\\n]]", " "));
        
        Graphics2D g = new NoOpGraphics2D() {
            public void draw(Shape shape) {
                transcodeShape(shape);
                printWriter.println("g.draw(shape);");
            }

            public void fill(Shape shape) {
                transcodeShape(shape);
                printWriter.println("g.fill(shape);");
            }

            public void setComposite(Composite composite) {
                transcodeCompositeChange((AlphaComposite) composite);
            }

            public void setPaint(Paint paint) {
                transcodePaintChange(paint);
            }

            public void setStroke(Stroke stroke) {
                transcodeStrokeChange(stroke);
            }

            public Object getRenderingHint(RenderingHints.Key key) {
                if (key.equals(RenderingHints.KEY_TEXT_ANTIALIASING)) {
                    return RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
                } else if (key.equals(RenderingHints.KEY_STROKE_CONTROL)) {
                    return RenderingHints.VALUE_STROKE_PURE;
                } else {
                    throw new UnsupportedOperationException("Unhandled hint: " + key.toString());
                }
            }
        };
        
        text.getTextPainter().paint(text, g);
    }
}
