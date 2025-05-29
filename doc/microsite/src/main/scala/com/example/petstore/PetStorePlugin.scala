package com.example.petstore

import izumi.distage.plugins.PluginDef

object PetStorePlugin extends PluginDef {
  make[PetRepository]
  make[PetStoreService]
  make[PetStoreController]
}

class PetRepository
class PetStoreService
class PetStoreController {
  def run() = println("PetStoreController: running!")
}
