for tag in $(gh release list | awk 'NR>2 {print $(NF-2)}'); do
  asset=$(gh release view $tag --json assets -q '.assets[0].name' 2>/dev/null)
  if [ -n "$asset" ] && [ "$asset" != "null" ]; then
    new_name="Flash_EEPROM_Tool_${tag}.apk"
    if [ "$asset" != "$new_name" ]; then
      echo "Renaming $asset to $new_name in $tag..."
      gh release download $tag -p "$asset"
      mv "$asset" "$new_name"
      gh release upload $tag "$new_name" --clobber
      if [ $? -eq 0 ]; then
        gh release delete-asset $tag "$asset" -y
      fi
      rm "$new_name"
    fi
  fi
done
