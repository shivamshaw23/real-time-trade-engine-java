# Bug Fixes Applied

## Summary

Fixed several potential bugs and added defensive checks throughout the codebase to improve robustness and prevent runtime exceptions.

## Bugs Fixed

### 1. Null Price Check for Limit Orders
**Location**: `MatchingEngine.matchLimit()`
**Issue**: If a limit order somehow had a null price, it would cause NullPointerException when comparing prices.
**Fix**: Added defensive check at the start of `matchLimit()` to reject orders with null prices.

```java
BigDecimal limitPrice = incomingOrder.getPrice();
if (limitPrice == null) {
    logger.error("Limit order {} has null price, rejecting", incomingOrder.getOrderId());
    incomingOrder.setStatus("rejected");
    // ... persist rejection
    return;
}
```

### 2. Null Quantity Checks
**Location**: `MatchingEngine.handlePlaceOrder()`, `matchMarket()`, `matchLimit()`
**Issue**: Orders with null quantities would cause NullPointerException.
**Fix**: Added defensive checks to validate quantity before processing.

### 3. Null FilledQuantity Handling
**Location**: `MatchingEngine.matchMarket()`, `matchLimit()`
**Issue**: If `filledQuantity` is null, subtraction would fail.
**Fix**: Use null-safe extraction:
```java
BigDecimal filledQty = incomingOrder.getFilledQuantity() != null 
    ? incomingOrder.getFilledQuantity() 
    : BigDecimal.ZERO;
BigDecimal remainingQty = incomingOrder.getQuantity().subtract(filledQty);
```

### 4. Invalid Side Validation
**Location**: `MatchingEngine.matchMarket()`, `matchLimit()`
**Issue**: Invalid side values could cause unexpected behavior.
**Fix**: Added validation to ensure side is either "buy" or "sell".

### 5. OrderBook Cancel Order Bug
**Location**: `OrderBook.cancelOrder()`
**Issue**: The logic for removing empty price levels was inefficient and could miss cases.
**Fix**: Improved the check to use `containsKey()` before accessing:
```java
if (bids.containsKey(price)) {
    PriceLevel bidLevel = bids.get(price);
    if (bidLevel != null && bidLevel.isEmpty()) {
        bids.remove(price);
    }
} else if (asks.containsKey(price)) {
    PriceLevel askLevel = asks.get(price);
    if (askLevel != null && askLevel.isEmpty()) {
        asks.remove(price);
    }
}
```

### 6. PriceLevel Null Checks
**Location**: `PriceLevel.addOrder()`, `removeOrder()`
**Issue**: Null entries could cause issues.
**Fix**: 
- Added null check in `addOrder()` with exception
- Added null check for `remainingQty` in `removeOrder()`

### 7. Missing Import
**Location**: `OrderController.java`
**Issue**: Missing import for `RateLimitService` and `HttpServletRequest`.
**Fix**: Added imports:
```java
import com.tradeengine.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
```

### 8. Invalid Instrument Check
**Location**: `MatchingEngine.handlePlaceOrder()`
**Issue**: Orders with null or blank instruments could cause issues.
**Fix**: Added validation to reject orders with invalid instruments.

## Defensive Programming Improvements

All matching methods now include:
- ✅ Null checks for critical fields (price, quantity, side, instrument)
- ✅ Validation of business rules before processing
- ✅ Proper error logging
- ✅ Status updates to "rejected" for invalid orders
- ✅ Safe persistence of rejected orders

## Impact

These fixes prevent:
- NullPointerExceptions in matching engine
- Invalid orders from being processed
- Data corruption from bad input
- Crashes from edge cases

The code is now more robust and handles edge cases gracefully.

