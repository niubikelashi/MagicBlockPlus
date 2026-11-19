package io.github.niubikelashi.magicblockplus.core;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** food.yml 里的一条魔法食物定义。 */
public class FoodDef {

    public final String materialName;
    public final Material material;
    public final int uses;
    public final String name;
    public final List<String> lore;

    public FoodDef(String materialName, Material material, int uses, String name, List<String> lore) {
        this.materialName = materialName;
        this.material = material;
        this.uses = uses;
        this.name = name;
        this.lore = lore == null ? new ArrayList<>() : lore;
    }
}
