package de.dkfz.roddy.knowledge.files;

/**
 * A file object tuple designed for (generic) job calls with 5 entries
 */
public class Tuple7<X extends FileObject, Y extends FileObject, Z extends FileObject, R extends FileObject, Q extends FileObject, W extends FileObject, S extends FileObject> extends Tuple4<X, Y, Z, R> {
    public Q value4;

    private W value5;

    private S value6;

    public Tuple7(X value0, Y value1, Z value2, R value3, Q value4, W value5) {
        super(value0, value1, value2, value3);
        this.value4 = value4;
        this.value5 = value5;
        this.value6 = value6;
    }
}

