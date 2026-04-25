package com.example.petstore

import izumi.distage.plugins.PluginDef

object PetStorePlugin extends PluginDef {
  make[PetRepository].fromSelf
  make[PetStoreService].fromSelf
  make[PetStoreController].fromSelf
}

class PetRepository
class PetStoreService
class PetStoreController {
  def run() = println("PetStoreController: running!")
}
