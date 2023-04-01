package com.monkeyhand.puppet.format;

import android.text.Spanned;

class LengthExceededException extends RuntimeException {
    protected final Spanned tail;

    LengthExceededException(Spanned tail) {
        this.tail = tail;
    }

    Spanned getTail() {
        return tail;
    }
}
