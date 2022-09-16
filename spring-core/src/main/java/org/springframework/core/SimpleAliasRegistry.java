/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Simple implementation of the {@link AliasRegistry} interface.
 * <p>Serves as base class for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * implementations.
 *
 * @author Juergen Hoeller
 * @author Qimiao Chen
 * @since 2.5.2
 */
public class SimpleAliasRegistry implements AliasRegistry {

	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Map from alias to canonical name.
	 *
	 * 存储别名与本命的映射关系
	 * 使用一个 ConcurrentHashMap 来存储，保证线程安全
	 * 存储方式（映射关系）为：别名 -> 本名，一个本名可以对应多个别名。
	 * 例如成龙有别名元楼、陈元龙，那么映射关系就是：
	 * 元楼 -> 成龙
	 * 陈元龙 -> 成龙
	 */
	private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);

	/**
	 * 注册一个别名，会在 aliasMap 中添加一个映射关系
	 *
	 * @param name the canonical name
	 * @param alias the alias to be registered
	 */
	@Override
	public void registerAlias(String name, String alias) {
		Assert.hasText(name, "'name' must not be empty");
		Assert.hasText(alias, "'alias' must not be empty");
		// synchronized 锁住 aliasMap，保证对 aliasMap 的操作是原子的，线程安全
		synchronized (this.aliasMap) {
			// 如果别名和本名一样，就在 aliasMap 中移除这个别名 -> 本名映射关系
			if (alias.equals(name)) {
				this.aliasMap.remove(alias);
				if (logger.isDebugEnabled()) {
					logger.debug("Alias definition '" + alias + "' ignored since it points to same name");
				}
			}
			else {
				// 如果别名和本名不一样。尝试在 aliasMap 中获取别名对应的本名。
				String registeredName = this.aliasMap.get(alias);
				// 如果 aliasMap 中此别名已经有了映射关系，则进行处理
				if (registeredName != null) {
					// 此别名已有映射关系对应的本名，是否是本次要添加的本名。即判断本次要添加的映射关系，在 aliasMap 中是否已存在。
					if (registeredName.equals(name)) {
						// An existing alias - no need to re-register 本次要添加的映射关系已经存在，无需处理
						return;
					}
					// 走到这里说明 aliasMap 中已有映射关系，别名对应的本名不是本次要添加的。判断是否允许已有映射关系的别名，覆盖之前的映射关系，重新映射到别的本名上，默认允许。
					if (!allowAliasOverriding()) {
						throw new IllegalStateException("Cannot define alias '" + alias + "' for name '" +
								name + "': It is already registered for name '" + registeredName + "'.");
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Overriding alias '" + alias + "' definition for registered name '" +
								registeredName + "' with new target name '" + name + "'");
					}
				}
				// 检查是否存在别名指向循环，如 A->B B->C C->A 就是一个循环。这种情况是不允许的，存在循环的时候会抛出 IllegalStateException
				checkForAliasCircle(name, alias);
				// 在 aliasMap 中添加映射关系
				this.aliasMap.put(alias, name);
				if (logger.isTraceEnabled()) {
					logger.trace("Alias definition '" + alias + "' registered for name '" + name + "'");
				}
			}
		}
	}

	/**
	 * Determine whether alias overriding is allowed.
	 *
	 * 是否允许已有映射关系的别名，覆盖之前的映射关系，重新映射到别的本名上，默认允许。
	 *
	 * <p>Default is {@code true}.
	 */
	protected boolean allowAliasOverriding() {
		return true;
	}

	/**
	 * Determine whether the given name has the given alias registered.
	 *
	 * 递归地检查一个本名是否有指定别名
	 * 为什么递归？因为别名是可以递归注册的。
	 * 例如 C 是本名，B 是 C 的别名，A 是 B 的别名。即 A->B B->C，那么 A 也是 C 的别名
	 *
	 * @param name the name to check
	 * @param alias the alias to look for
	 * @since 4.2.1
	 */
	public boolean hasAlias(String name, String alias) {
		String registeredName = this.aliasMap.get(alias);
		/*
		 * 第一次递归时是使用指定的别名 alias 作为 key 查找的
		 * 递归会不断地在 aliasMap 中找 key -> value, key -> value
		 * 当在某次递归中发现 name 本名和本次递归中的 value 相等时，说明此本名 name 与指定的别名 alias 存在映射关系
		 */
		return ObjectUtils.nullSafeEquals(registeredName, name) || (registeredName != null
				&& hasAlias(name, registeredName));
	}

	/**
	 * 移除一个别名，即从 aliasMap 中移除一个映射关系
	 *
	 * @param alias the alias to remove
	 */
	@Override
	public void removeAlias(String alias) {
		// 使用 synchronized 锁住 aliasMap，原子地操作 aliasMap，保证线程安全
		synchronized (this.aliasMap) {
			String name = this.aliasMap.remove(alias);
			if (name == null) {
				throw new IllegalStateException("No alias '" + alias + "' registered");
			}
		}
	}

	/**
	 * 判断一个 name 是否是别名
	 *
	 * @param name the name to check
	 * @return
	 */
	@Override
	public boolean isAlias(String name) {
		// 把 name 看成 aliasMap 的 key，判断 aliasMap 是否包含这个 key
		return this.aliasMap.containsKey(name);
	}

	/**
	 * 获取一个本名的所有别名，调用了 retrieveAliases 方法
	 *
	 * @param name the name to check for aliases
	 * @return
	 */
	@Override
	public String[] getAliases(String name) {
		List<String> result = new ArrayList<>();
		// 调用了 retrieveAliases 方法，并在调用期间锁住 aliasMap，保证期间 aliasMap 不会发生变更
		synchronized (this.aliasMap) {
			retrieveAliases(name, result);
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Transitively retrieve all aliases for the given name.
	 *
	 * 找到一个本名的所有别名，通过循环遍历 + 递归的方式。
	 * 为什么需要循环遍历？因为 aliasMap 的 key 是别名，value 是本名。我们现在想通过本名找别名，本质上是通过 value 找 key。
	 * 为什么需要递归？因为在 aliasMap 中，通过 value 找到的 key 别名，可能又作为 value 被其他 key 别名所指向，需要递归看一下。
	 *
	 * @param name the target name to find aliases for
	 * @param result the resulting aliases list
	 */
	private void retrieveAliases(String name, List<String> result) {
		this.aliasMap.forEach((alias, registeredName) -> {
			if (registeredName.equals(name)) {
				result.add(alias);
				retrieveAliases(alias, result);
			}
		});
	}

	/**
	 * Resolve all alias target names and aliases registered in this
	 * registry, applying the given {@link StringValueResolver} to them.
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 *
	 * 使用字符串解析器如占位符，解析所有别名
	 *
	 * @param valueResolver the StringValueResolver to apply
	 */
	public void resolveAliases(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		synchronized (this.aliasMap) {
			Map<String, String> aliasCopy = new HashMap<>(this.aliasMap);
			aliasCopy.forEach((alias, registeredName) -> {
				String resolvedAlias = valueResolver.resolveStringValue(alias);
				String resolvedName = valueResolver.resolveStringValue(registeredName);
				if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
					this.aliasMap.remove(alias);
				}
				else if (!resolvedAlias.equals(alias)) {
					String existingName = this.aliasMap.get(resolvedAlias);
					if (existingName != null) {
						if (existingName.equals(resolvedName)) {
							// Pointing to existing alias - just remove placeholder
							this.aliasMap.remove(alias);
							return;
						}
						throw new IllegalStateException(
								"Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
								"') for name '" + resolvedName + "': It is already registered for name '" +
								registeredName + "'.");
					}
					checkForAliasCircle(resolvedName, resolvedAlias);
					this.aliasMap.remove(alias);
					this.aliasMap.put(resolvedAlias, resolvedName);
				}
				else if (!registeredName.equals(resolvedName)) {
					this.aliasMap.put(alias, resolvedName);
				}
			});
		}
	}

	/**
	 * Check whether the given name points back to the given alias as an alias
	 * in the other direction already, catching a circular reference upfront
	 * and throwing a corresponding IllegalStateException.
	 *
	 * 检查是否存在别名指向循环，如 A->B B->C C->A 就是一个循环。
	 * 这种情况是不允许的，存在循环的时候会抛出 IllegalStateException
	 *
	 * @param name the candidate name
	 * @param alias the candidate alias
	 * @see #registerAlias
	 * @see #hasAlias
	 */
	protected void checkForAliasCircle(String name, String alias) {
		if (hasAlias(alias, name)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Circular reference - '" +
					name + "' is a direct or indirect alias for '" + alias + "' already");
		}
	}

	/**
	 * Determine the raw name, resolving aliases to canonical names.
	 *
	 * 传入一个 name，不管这个 name 是别名还是本名，此方法将获得真实的本名
	 *
	 * @param name the user-specified name
	 * @return the transformed name
	 */
	public String canonicalName(String name) {
		// 返回值，真实的本名
		String canonicalName = name;
		// Handle aliasing...
		String resolvedName;
		do {
			// 先假设入参 name 是一个别名，尝试去 aliasMap 中找对应的本名
			resolvedName = this.aliasMap.get(canonicalName);
			if (resolvedName != null) {
				canonicalName = resolvedName;
			}
		}
		/*
		 * 由于别名可能存在递归注册的情况，因此循环地：寻找映射关系 key -> value，value 当作新的 key，继续寻找映射关系 key -> value
		 * 直到找不到映射关系为止，此时找到了真正的 value 本名
		 */
		while (resolvedName != null);
		return canonicalName;
	}

}
