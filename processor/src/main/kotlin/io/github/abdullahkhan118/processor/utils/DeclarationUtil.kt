package io.github.abdullahkhan118.processor.utils

import com.google.devtools.ksp.symbol.KSDeclaration


fun addPackageDeclaration(declaration: KSDeclaration) = declaration.packageName.asString().let { packageName ->
    if (packageName.isNotEmpty()) "package $packageName \n" else "package empty"
}

