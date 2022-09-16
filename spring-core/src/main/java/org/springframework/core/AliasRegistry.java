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

/**
 * Common interface for managing aliases. Serves as a super-interface for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}.
 *
 * 作为 IOC 容器相关的最顶层接口之一，这个接口声明了管理别名的一些方法
 * 主要作用是将名字-别名映射存到内存中，提供查找和校验的接口
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 */
public interface AliasRegistry {

	/**
	 * Given a name, register an alias for it.
	 *
	 * 为一个 name 注册别名
	 *
	 * @param name the canonical name
	 * @param alias the alias to be registered
	 * @throws IllegalStateException if the alias is already in use
	 * and may not be overridden
	 */
	void registerAlias(String name, String alias);

	/**
	 * Remove the specified alias from this registry.
	 *
	 * 删除一个指定的别名
	 *
	 * @param alias the alias to remove
	 * @throws IllegalStateException if no such alias was found
	 */
	void removeAlias(String alias);

	/**
	 * Determine whether the given name is defined as an alias
	 * (as opposed to the name of an actually registered component).
	 *
	 * 确定一个名字是否是别名
	 *
	 * @param name the name to check
	 * @return whether the given name is an alias
	 */
	boolean isAlias(String name);

	/**
	 * Return the aliases for the given name, if defined.
	 *
	 * 返回一个名字注册的别名列表
	 *
	 * @param name the name to check for aliases
	 * @return the aliases, or an empty array if none
	 */
	String[] getAliases(String name);

}
