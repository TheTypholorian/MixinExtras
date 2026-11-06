package com.llamalad7.mixinextras.sugar;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Allows modification of stack values at jump targets.</p>
 * For example, this code:
 * <pre>
 * {@code
 * instance.someMethod(123);
 * }
 * </pre>
 * compiles into
 * <pre>
 * {@code
 * ALOAD [instance]
 * BIPUSH 123
 * INVOKE_VIRTUAL [someMethod]
 * }
 * </pre>
 * Jumping before the {@code someMethod} call will not have {@code instance} and {@code 123} on the stack, therefore causing an error.
 * By default, jumping sets all stack values at the target to 0 (for primitives) or null (for objects). That would cause a NullPointerException in this case. We can fix this in one of 2 ways:
 * <ul>
 *     <li>
 *         <b>Option one</b> is to enable {@code shiftBeforeStack} inside the {@link Jump} annotation, which shifts the injection target backwards until the stack is empty.
 *         This causes the injection to happen before the {@code ALOAD}, and run normally.
 *     </li>
 *     <li>
 *         <b>Option two</b> is to use a {@link JumpStack} sugar, which would look like this for the example code:
 *         <pre>
 *         {@code
 *         @JumpStack(index = 0) StackRef<Instance> stack1,
 *         @JumpStack(index = 1) IntStackRef stack2
 *         }
 *         </pre>
 *         then set the values of the stack refs inside your injected method.<br>
 *         Note that the value will only be used if the jump is executed, otherwise it is ignored.
 *     </li>
 * </ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface JumpStack {
    /**
     * The index in the stack where the value should be set.
     */
    int index();

    /**
     * <p>The 0-based index of jump to target.</p>
     * Example:
     * <pre>
     * {@code
     * @Jump(...) JumpInfo jump1, // Jump index 0
     * @Local(jump = 0) IntStackRef local1,
     * @JumpStack(index = 0) FloatStackRef stack1,
     *
     * @Jump(...) JumpInfo jump2, // Jump index 1
     * @Local(jump = 1) StackRef<String> local2
     * }
     * </pre>
     *
     * @see Jump
     */
    int jump() default 0;
}
