/*
 * Copyright (c) 2009 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.lang.annotation.*;
import java.io.*;
import java.net.URL;
import java.util.List;

import com.sun.tools.classfile.*;

/*
 * @test Wildcards
 * @bug 6843077
 * @summary test that annotations target wildcards get emitted to classfile
 */
public class Wildcards {
    public static void main(String[] args) throws Exception {
        new Wildcards().run();
    }

    public void run() throws Exception {
        ClassFile cf = getClassFile("NewTypeArguments$Test.class");
        test(cf);
        for (Field f : cf.fields) {
            test(cf, f);
        }
        for (Method m: cf.methods) {
            test(cf, m);
        }

        countAnnotations();

        if (errors > 0)
            throw new Exception(errors + " errors found");
        System.out.println("PASSED");
    }

    ClassFile getClassFile(String name) throws IOException, ConstantPoolException {
        URL url = getClass().getResource(name);
        InputStream in = url.openStream();
        try {
            return ClassFile.read(in);
        } finally {
            in.close();
        }
    }

    /************ Helper annotations counting methods ******************/
    void test(ClassFile cf) {
        test(cf, Attribute.RuntimeVisibleTypeAnnotations, true);
        test(cf, Attribute.RuntimeInvisibleTypeAnnotations, false);
    }

    void test(ClassFile cf, Method m) {
        test(cf, m, Attribute.RuntimeVisibleTypeAnnotations, true);
        test(cf, m, Attribute.RuntimeInvisibleTypeAnnotations, false);
    }

    void test(ClassFile cf, Field m) {
        test(cf, m, Attribute.RuntimeVisibleTypeAnnotations, true);
        test(cf, m, Attribute.RuntimeInvisibleTypeAnnotations, false);
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    void test(ClassFile cf, String name, boolean visible) {
        int index = cf.attributes.getIndex(cf.constant_pool, name);
        if (index != -1) {
            Attribute attr = cf.attributes.get(index);
            assert attr instanceof RuntimeTypeAnnotations_attribute;
            RuntimeTypeAnnotations_attribute tAttr = (RuntimeTypeAnnotations_attribute)attr;
            all += tAttr.annotations.length;
            if (visible)
                visibles += tAttr.annotations.length;
            else
                invisibles += tAttr.annotations.length;
        }
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    void test(ClassFile cf, Method m, String name, boolean visible) {
        int index = m.attributes.getIndex(cf.constant_pool, name);
        if (index != -1) {
            Attribute attr = m.attributes.get(index);
            assert attr instanceof RuntimeTypeAnnotations_attribute;
            RuntimeTypeAnnotations_attribute tAttr = (RuntimeTypeAnnotations_attribute)attr;
            all += tAttr.annotations.length;
            if (visible)
                visibles += tAttr.annotations.length;
            else
                invisibles += tAttr.annotations.length;
        }
    }

    // test the result of Attributes.getIndex according to expectations
    // encoded in the method's name
    void test(ClassFile cf, Field m, String name, boolean visible) {
        int index = m.attributes.getIndex(cf.constant_pool, name);
        if (index != -1) {
            Attribute attr = m.attributes.get(index);
            assert attr instanceof RuntimeTypeAnnotations_attribute;
            RuntimeTypeAnnotations_attribute tAttr = (RuntimeTypeAnnotations_attribute)attr;
            all += tAttr.annotations.length;
            if (visible)
                visibles += tAttr.annotations.length;
            else
                invisibles += tAttr.annotations.length;
        }
    }

    void countAnnotations() {
        int expected_all = expected_visibles + expected_invisibles;

        if (expected_all != all) {
            errors++;
            System.err.println("expected " + expected_all
                    + " annotations but found " + all);
        }

        if (expected_visibles != visibles) {
            errors++;
            System.err.println("expected " + expected_visibles
                    + " visibles annotations but found " + visibles);
        }

        if (expected_invisibles != invisibles) {
            errors++;
            System.err.println("expected " + expected_invisibles
                    + " invisibles annotations but found " + invisibles);
        }

    }

    int errors;
    int all;
    int visibles;
    int invisibles;

    /*********************** Test class *************************/
    static int expected_invisibles = 3;
    static int expected_visibles = 0;
    static class Test {
        @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
        @interface A {}

        List<? extends @A Number> f;

        List<? extends @A Object> test(List<? extends @A Number> p) {
            List<? extends @A Object> l;  // not counted... gets optimized away
            return null;
        }
    }
}
