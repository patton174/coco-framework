package io.github.coco.feature.concurrencylimit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.mock.web.MockAsyncContext;

final class TestAsyncContext extends MockAsyncContext {

    private final List<AsyncListener> listeners = new ArrayList<>();

    private final boolean rejectListenerBindings;

    private final boolean rejectCompletion;

    private int completeCalls;

    TestAsyncContext(ServletRequest request, ServletResponse response) {
        this(request, response, false, false);
    }

    private TestAsyncContext(ServletRequest request, ServletResponse response, boolean rejectListenerBindings,
            boolean rejectCompletion) {
        super(request, response);
        this.rejectListenerBindings = rejectListenerBindings;
        this.rejectCompletion = rejectCompletion;
    }

    int listenerCount() {
        return this.listeners.size();
    }

    void timeoutLifecycle() throws IOException {
        notifyListeners(listener -> listener.onTimeout(event(this)));
    }

    void errorLifecycle(Throwable throwable) throws IOException {
        notifyListeners(listener -> listener.onError(new AsyncEvent(this, getRequest(), getResponse(), throwable)));
    }

    TestAsyncContext startNewAsyncCycle() throws IOException {
        TestAsyncContext next = new TestAsyncContext(getRequest(), getResponse());
        notifyListeners(listener -> listener.onStartAsync(event(next)));
        return next;
    }

    TestAsyncContext startNewAsyncCycleWithBindingFailure() throws IOException {
        return startNewAsyncCycleWithBindingFailure(false);
    }

    TestAsyncContext startNewAsyncCycleWithBindingAndCompletionFailure() throws IOException {
        return startNewAsyncCycleWithBindingFailure(true);
    }

    int completeCalls() {
        return this.completeCalls;
    }

    @Override
    public void complete() {
        this.completeCalls++;
        if (this.rejectCompletion) {
            throw new IllegalStateException("test async completion failure");
        }
        try {
            completeLifecycle();
        }
        catch (IOException exception) {
            throw new IllegalStateException("test async listener completion failed", exception);
        }
    }

    private TestAsyncContext startNewAsyncCycleWithBindingFailure(boolean completionFailure) throws IOException {
        TestAsyncContext next = new TestAsyncContext(getRequest(), getResponse(), true, completionFailure);
        notifyListeners(listener -> listener.onStartAsync(event(next)));
        return next;
    }

    void completeLifecycle() throws IOException {
        notifyListeners(listener -> listener.onComplete(event(this)));
    }

    @Override
    public void addListener(AsyncListener listener) {
        rejectListenerBinding();
        this.listeners.add(listener);
    }

    @Override
    public void addListener(AsyncListener listener, ServletRequest suppliedRequest, ServletResponse suppliedResponse) {
        rejectListenerBinding();
        this.listeners.add(listener);
    }

    private AsyncEvent event(TestAsyncContext context) {
        return new AsyncEvent(context, getRequest(), getResponse());
    }

    private void notifyListeners(AsyncListenerCallback callback) throws IOException {
        for (AsyncListener listener : List.copyOf(this.listeners)) {
            callback.notify(listener);
        }
    }

    private void rejectListenerBinding() {
        if (this.rejectListenerBindings) {
            throw new IllegalStateException("test async listener binding failure");
        }
    }

    @FunctionalInterface
    private interface AsyncListenerCallback {

        void notify(AsyncListener listener) throws IOException;
    }
}
