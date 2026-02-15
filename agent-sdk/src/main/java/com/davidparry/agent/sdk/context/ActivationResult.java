package com.davidparry.agent.sdk.context;

import com.davidparry.agent.protocol.SessionResult;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Result of activating an agent session.
 * 
 * For chained agents, the future completes with a {@link ChainedSessionResult}
 * containing all intermediate results. For single agents, the chain
 * will contain just one execution snapshot.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * ActivationResult activation = context.activateAgent("my_agent", params, listener);
 * 
 * // Wait for completion
 * ChainedSessionResult chainResult = activation.getChainResult();
 * 
 * // Or check status without blocking
 * if (activation.isCompletedSuccessfully()) {
 *     SessionResult finalResult = activation.getFinalResult().orElseThrow();
 *     System.out.println("Result: " + finalResult.getContent());
 * }
 * 
 * // Get all results from a chain
 * List<SessionResult> allResults = activation.getAllResults();
 * }</pre>
 * 
 * @param sessionId the unique session identifier, available immediately
 * @param resultFuture a CompletableFuture that completes when the chain finishes
 */
public record ActivationResult(
    String sessionId,
    CompletableFuture<ChainedSessionResult> resultFuture
) {
    
    /**
     * Checks if the session is still pending (future not yet completed).
     * 
     * @return true if the session is still running
     */
    public boolean isPending() {
        return !resultFuture.isDone();
    }
    
    /**
     * Checks if the chain completed successfully (all agents succeeded).
     * 
     * @return true if completed without exception and all agents succeeded
     */
    public boolean isCompletedSuccessfully() {
        if (!resultFuture.isDone() || resultFuture.isCompletedExceptionally()) {
            return false;
        }
        ChainedSessionResult result = resultFuture.join();
        return result.isFullySuccessful();
    }
    
    /**
     * Checks if the chain failed (exceptionally or with failure status).
     * 
     * @return true if the chain failed
     */
    public boolean isFailed() {
        if (resultFuture.isCompletedExceptionally()) {
            return true;
        }
        if (resultFuture.isDone()) {
            ChainedSessionResult result = resultFuture.join();
            return result.status() == ChainedSessionResult.ChainStatus.FAILED;
        }
        return false;
    }
    
    /**
     * Checks if the chain has partial results (some agents succeeded before failure).
     * 
     * @return true if there are partial results
     */
    public boolean hasPartialResults() {
        if (!resultFuture.isDone() || resultFuture.isCompletedExceptionally()) {
            return false;
        }
        return resultFuture.join().hasPartialResults();
    }
    
    /**
     * Gets the final SessionResult if the chain completed.
     * For backward compatibility with single-agent usage.
     * 
     * @return Optional containing the last result, or empty if not completed
     */
    public Optional<SessionResult> getFinalResult() {
        if (!resultFuture.isDone() || resultFuture.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return resultFuture.join().getFinalResult();
    }
    
    /**
     * Gets all successful results from the chain in execution order.
     * 
     * @return list of successful session results (empty if not completed or failed)
     */
    public List<SessionResult> getAllResults() {
        if (!resultFuture.isDone() || resultFuture.isCompletedExceptionally()) {
            return List.of();
        }
        return resultFuture.join().getSuccessfulResults();
    }
    
    /**
     * Gets the full chain result with all metadata.
     * Blocks until completion.
     * 
     * @return the complete chain result
     * @throws java.util.concurrent.CompletionException if the future completed exceptionally
     */
    public ChainedSessionResult getChainResult() {
        return resultFuture.join();
    }
    
    /**
     * Gets the full chain result with a timeout.
     * 
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return the complete chain result
     * @throws TimeoutException if the wait timed out
     * @throws java.util.concurrent.ExecutionException if the future completed exceptionally
     * @throws InterruptedException if the current thread was interrupted
     */
    public ChainedSessionResult getChainResult(long timeout, TimeUnit unit) 
            throws TimeoutException, InterruptedException, java.util.concurrent.ExecutionException {
        return resultFuture.get(timeout, unit);
    }
    
    /**
     * Gets the chain status if completed.
     * 
     * @return Optional containing the status, or empty if not completed
     */
    public Optional<ChainedSessionResult.ChainStatus> getStatus() {
        if (!resultFuture.isDone() || resultFuture.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return Optional.of(resultFuture.join().status());
    }
    
    /**
     * Gets the number of agents that executed in the chain.
     * 
     * @return count of executed agents, or 0 if not completed
     */
    public int getExecutedAgentCount() {
        if (!resultFuture.isDone() || resultFuture.isCompletedExceptionally()) {
            return 0;
        }
        return resultFuture.join().getExecutedAgentCount();
    }
    
    /**
     * Registers a callback to be executed when the chain completes.
     * 
     * @param action the action to perform with the chain result
     * @return this ActivationResult for chaining
     */
    public ActivationResult whenComplete(java.util.function.Consumer<ChainedSessionResult> action) {
        resultFuture.thenAccept(action);
        return this;
    }
    
    /**
     * Registers callbacks for success and failure cases.
     * 
     * @param onSuccess action to perform on successful completion
     * @param onFailure action to perform on failure
     * @return this ActivationResult for chaining
     */
    public ActivationResult whenComplete(
            java.util.function.Consumer<ChainedSessionResult> onSuccess,
            java.util.function.Consumer<Throwable> onFailure) {
        resultFuture.whenComplete((result, error) -> {
            if (error != null) {
                onFailure.accept(error);
            } else {
                onSuccess.accept(result);
            }
        });
        return this;
    }
}
