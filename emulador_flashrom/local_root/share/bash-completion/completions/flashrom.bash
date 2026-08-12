# Completion file for bash
#
# This file is part of the flashrom project.
#
# SPDX-License-Identifier: GPL-2.0-or-later
# SPDX-FileCopyrightText: 2022 Alexander Goncharov <chat@joursoir.net>

_flashrom()
{
	local cur prev OPTS
	COMPREPLY=()
	cur="${COMP_WORDS[COMP_CWORD]}"
	prev="${COMP_WORDS[COMP_CWORD-1]}"
	case $prev in
		'-r'|'--read'|'-w'|'--write'|'-v'|'--verify'|'-l'|'--layout'| \
		'--fmap-file'|'-o'|'--output'|'--flash-contents'|'--read-repeated')
			local IFS=$'\n'
			compopt -o filenames
			COMPREPLY=( $(compgen -f -- $cur) )
			return 0
			;;
		'-c'|'--chip'|'--wp-range'|'--wp-region'|'-i'|'--include')
			return 0
			;;
		'-p'|'--programmer')
			COMPREPLY=( $(compgen -W "asm106x atavia buspirate_spi ch341a_spi ch347_spi dediprog developerbox_spi digilent_spi dirtyjtag_spi drkaiser dummy gfxnvidia internal it8212 linux_mtd linux_spi nic3com nicintel nicintel_eeprom nicintel_spi nicrealtek nv_sma_spi ogp_spi pickit2_spi pony_spi raiden_debug_spi rayer_spi satamv satasii serprog spidriver stlinkv3_spi " -- $cur) )
			return 0
			;;
		'-h'|'--help'|'-R'|'--version'|'-L'|'--list-supported')
			return 0
			;;
	esac
	OPTS="--help
		--version
		--read
		--write
		--verify
		--erase
		--verbose
		--chip
		--force
		--noverify
		--noverify-all
		--extract
		--layout
		--wp-disable
		--wp-enable
		--wp-list
		--wp-status
		--wp-range
		--wp-region
		--flash-name
		--flash-size
		--fmap
		--fmap-file
		--fmap-verify
		--ifd
		--include
		--output
		--flash-contents
		--list-supported
		--progress
		--programmer
		--read-repeated"
	COMPREPLY=( $(compgen -W "${OPTS[*]}" -- $cur) )
	return 0
}

complete -F _flashrom flashrom
