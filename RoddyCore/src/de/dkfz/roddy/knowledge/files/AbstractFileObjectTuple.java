/*
 * Copyright (c) 2016 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.files;

import de.dkfz.roddy.core.ExecutionContext;

/**
 * An abstract base class for tuple objects
 */
public abstract class AbstractFileObjectTuple extends FileObject {

    public AbstractFileObjectTuple(ExecutionContext executionContext) {
        super(executionContext);
    }

    @Override
    public void runDefaultOperations() {

    }
}
