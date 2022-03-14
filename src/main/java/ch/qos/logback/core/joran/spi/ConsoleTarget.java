/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.joran.spi;

import java.io.IOException;
import java.io.OutputStream;
import org.etnaframework.core.util.ThreadUtils;

/**
 * The set of console output targets.

 * @author Ruediger Dohna
 * @author Ceki G&uuml;lc&uuml;
 * @author Tom SH Liu
 * @author David Roussel
 */
public enum ConsoleTarget {

    // @CRACK 2015-01-08 由于System.out和System.err使用的是不同的PrintWriter，当混合输出时不能保证顺序
    // 此处加了同步处理，并加延迟以减少该问题，注意正式环境是不会在控制台大量输出日志的，不会导致性能问题

    SystemOut("System.out", new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            synchronized (System.out) {
                System.out.write(b);
                ThreadUtils.sleep(10);
            }
        }

        @Override
        public void write(byte b[]) throws IOException {
            synchronized (System.out) {
                System.out.write(b);
                ThreadUtils.sleep(10);
            }
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            synchronized (System.out) {
                System.out.write(b, off, len);
                ThreadUtils.sleep(10);
            }
        }

        @Override
        public void flush() throws IOException {
            synchronized (System.out) {
                System.out.flush();
            }
        }
    }),

    SystemErr("System.err", new OutputStream() {

        @Override
        public void write(int b) throws IOException {
            synchronized (System.out) {
                System.err.write(b);
                ThreadUtils.sleep(10);
            }
        }

        @Override
        public void write(byte b[]) throws IOException {
            synchronized (System.out) {
                System.err.write(b);
                ThreadUtils.sleep(10);
            }
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            synchronized (System.out) {
                System.err.write(b, off, len);
                ThreadUtils.sleep(10);
            }
        }

        @Override
        public void flush() throws IOException {
            synchronized (System.out) {
                System.err.flush();
            }
        }
    });

    // @CRACK end

    public static ConsoleTarget findByName(String name) {
        for (ConsoleTarget target : ConsoleTarget.values()) {
            if (target.name.equalsIgnoreCase(name)) {
                return target;
            }
        }
        return null;
    }

    private final String name;
    private final OutputStream stream;

    private ConsoleTarget(String name, OutputStream stream) {
        this.name = name;
        this.stream = stream;
    }

    public String getName() {
        return name;
    }

    public OutputStream getStream() {
        return stream;
    }

    @Override
    public String toString() {
        return name;
    }
}
