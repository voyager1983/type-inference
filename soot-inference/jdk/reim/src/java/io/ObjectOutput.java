package java.io;

import checkers.inference.reim.quals.*;

public interface ObjectOutput extends DataOutput {
    public void close() throws IOException;
    public void flush() throws IOException;
    public void write(@Readonly byte[] b) throws IOException;
    public void write(@Readonly byte[] b, int off, int len) throws IOException;
    public void write(int b) throws IOException;
    public void writeObject(@Readonly Object obj) throws IOException;
}
